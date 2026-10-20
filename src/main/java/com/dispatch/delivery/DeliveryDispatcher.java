package com.dispatch.delivery;

import com.dispatch.dto.message.NotificationMessage;
import com.dispatch.entity.DeliveryAttempt;
import com.dispatch.entity.Notification;
import com.dispatch.entity.enums.AttemptStatus;
import com.dispatch.entity.enums.NotificationStatus;
import com.dispatch.repository.DeliveryAttemptRepository;
import com.dispatch.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Coordinates notification delivery: checks current status, selects channel strategy,
 * executes dispatch, logs delivery attempts, and updates the notification state.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryDispatcher {

    private final DeliveryStrategyRegistry strategyRegistry;
    private final NotificationRepository notificationRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;

    @Transactional
    public DeliveryResult dispatch(NotificationMessage message) {
        Optional<Notification> notificationOpt = notificationRepository.findById(message.getNotificationId());

        if (notificationOpt.isEmpty()) {
            log.warn("Notification {} not found in database. Skipping delivery.", message.getNotificationId());
            return DeliveryResult.failure("NOT_FOUND", "Notification not found");
        }

        Notification notification = notificationOpt.get();

        // Check if notification was cancelled before processing
        if (notification.getStatus() == NotificationStatus.CANCELLED) {
            log.info("Notification {} is CANCELLED. Aborting delivery attempt.", notification.getId());
            return DeliveryResult.failure("CANCELLED", "Notification was cancelled");
        }

        // Check if already successfully delivered
        if (notification.getStatus() == NotificationStatus.DELIVERED) {
            log.info("Notification {} is already DELIVERED. Skipping duplicate attempt.", notification.getId());
            return DeliveryResult.success("Already delivered");
        }

        // Mark as PROCESSING
        notification.setStatus(NotificationStatus.PROCESSING);
        notification = notificationRepository.save(notification);

        // Calculate attempt number
        int attemptNumber = (int) deliveryAttemptRepository.countByNotificationId(notification.getId()) + 1;

        // Execute channel delivery strategy
        DeliveryStrategy strategy = strategyRegistry.getStrategy(message.getType());
        DeliveryResult result = strategy.deliver(message);

        // Record DeliveryAttempt audit log
        DeliveryAttempt attempt = DeliveryAttempt.builder()
                .notification(notification)
                .attemptNumber(attemptNumber)
                .status(result.isSuccess() ? AttemptStatus.SUCCESS : AttemptStatus.FAILURE)
                .providerResponse(result.getProviderResponse())
                .errorCode(result.getErrorCode())
                .errorMessage(result.getErrorMessage())
                .attemptedAt(Instant.now())
                .build();
        deliveryAttemptRepository.save(attempt);

        // Update Notification status
        NotificationStatus nextStatus = result.isSuccess() ? NotificationStatus.DELIVERED : NotificationStatus.FAILED;
        notification.setStatus(nextStatus);
        notificationRepository.save(notification);

        log.info("Notification {} transitioned to {} (attempt #{}, result: {})",
                notification.getId(), nextStatus, attemptNumber, result.isSuccess() ? "SUCCESS" : "FAILURE");

        return result;
    }
}
