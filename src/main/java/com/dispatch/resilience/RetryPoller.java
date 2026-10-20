package com.dispatch.resilience;

import com.dispatch.config.AppProperties;
import com.dispatch.dto.message.NotificationMessage;
import com.dispatch.entity.DeliveryAttempt;
import com.dispatch.entity.Notification;
import com.dispatch.entity.enums.AttemptStatus;
import com.dispatch.entity.enums.NotificationStatus;
import com.dispatch.repository.DeliveryAttemptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Scheduled background worker that polls for delivery attempts due for retry,
 * re-queuing them into the RabbitMQ notification exchange.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetryPoller {

    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final RabbitTemplate rabbitTemplate;
    private final AppProperties appProperties;

    @Scheduled(fixedDelayString = "${dispatch.recovery.retry-poll-interval-ms:5000}")
    @Transactional
    public int pollAndRetry() {
        Instant now = Instant.now();
        List<DeliveryAttempt> dueAttempts = deliveryAttemptRepository.findByStatusAndNextRetryAtBefore(
                AttemptStatus.RETRYING, now);

        if (dueAttempts.isEmpty()) {
            return 0;
        }

        log.info("Found {} delivery attempts due for retry.", dueAttempts.size());
        int dispatchedCount = 0;

        for (DeliveryAttempt attempt : dueAttempts) {
            Notification notification = attempt.getNotification();

            // Clear nextRetryAt so it will not be re-polled in subsequent ticks
            attempt.setNextRetryAt(null);
            deliveryAttemptRepository.save(attempt);

            if (notification == null) {
                continue;
            }

            // If notification was cancelled or already delivered, do not retry
            if (notification.getStatus() == NotificationStatus.CANCELLED
                    || notification.getStatus() == NotificationStatus.DELIVERED) {
                log.info("Notification {} is {}. Skipping retry.", notification.getId(), notification.getStatus());
                continue;
            }

            // Build and publish notification message to RabbitMQ notification queue
            NotificationMessage message = NotificationMessage.builder()
                    .notificationId(notification.getId())
                    .userId(notification.getUser().getId())
                    .type(notification.getType())
                    .recipientAddress(notification.getRecipientAddress())
                    .subject(notification.getSubject())
                    .body(notification.getBody())
                    .idempotencyKey(notification.getIdempotencyKey())
                    .channelMetadata(notification.getChannelMetadata())
                    .scheduledAt(notification.getScheduledAt())
                    .createdAt(notification.getCreatedAt())
                    .attemptCount(attempt.getAttemptNumber())
                    .build();

            try {
                rabbitTemplate.convertAndSend(
                        appProperties.getRabbitmq().getNotificationExchange(),
                        appProperties.getRabbitmq().getNotificationRoutingKey(),
                        message
                );
                dispatchedCount++;
                log.info("Re-enqueued notification {} for retry #{}",
                        notification.getId(), attempt.getAttemptNumber() + 1);
            } catch (Exception ex) {
                log.error("Failed to re-enqueue notification {} for retry: {}",
                        notification.getId(), ex.getMessage(), ex);
            }
        }

        return dispatchedCount;
    }
}
