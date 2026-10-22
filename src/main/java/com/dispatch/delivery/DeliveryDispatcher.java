package com.dispatch.delivery;

import com.dispatch.config.AppProperties;
import com.dispatch.dto.message.NotificationMessage;
import com.dispatch.entity.DeliveryAttempt;
import com.dispatch.entity.Notification;
import com.dispatch.entity.NotificationPreference;
import com.dispatch.entity.enums.AttemptStatus;
import com.dispatch.entity.enums.NotificationStatus;
import com.dispatch.entity.enums.NotificationType;
import com.dispatch.observability.NotificationMetrics;
import com.dispatch.preference.QuietHoursEvaluator;
import com.dispatch.repository.DeliveryAttemptRepository;
import com.dispatch.repository.NotificationPreferenceRepository;
import com.dispatch.repository.NotificationRepository;
import com.dispatch.resilience.BackoffCalculator;
import com.dispatch.resilience.DlqService;
import com.dispatch.resilience.ErrorClassifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Coordinates notification delivery: checks current status, selects channel strategy,
 * evaluates user channel opt-ins, applies address overrides, evaluates quiet hours,
 * executes dispatch, logs delivery attempts with retry backoff calculation,
 * and routes exhausted or permanent failures to the Dead Letter Queue (DLQ).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryDispatcher {

    private final DeliveryStrategyRegistry strategyRegistry;
    private final NotificationRepository notificationRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final QuietHoursEvaluator quietHoursEvaluator;
    private final ErrorClassifier errorClassifier;
    private final BackoffCalculator backoffCalculator;
    private final DlqService dlqService;
    private final AppProperties appProperties;
    private final NotificationMetrics notificationMetrics;

    @Transactional
    public DeliveryResult dispatch(NotificationMessage message) {
        Instant dispatchStartTime = Instant.now();
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

        // User Preferences Evaluation (Channel Opt-in, Address Override, Quiet Hours)
        Optional<NotificationPreference> prefOpt = preferenceRepository.findByUserId(notification.getUser().getId());
        if (prefOpt.isPresent()) {
            NotificationPreference pref = prefOpt.get();
            boolean urgent = isUrgent(message);

            // 1. Channel Opt-In Check (Non-urgent notifications honor user channel opt-outs)
            boolean channelEnabled = switch (message.getType()) {
                case EMAIL -> pref.isEmailEnabled();
                case SMS -> pref.isSmsEnabled();
                case IN_APP -> pref.isInAppEnabled();
            };

            if (!channelEnabled && !urgent) {
                log.info("User {} opted out of {} channel. Suppressing delivery for notification {}.",
                        notification.getUser().getId(), message.getType(), notification.getId());

                DeliveryAttempt attempt = DeliveryAttempt.builder()
                        .notification(notification)
                        .attemptNumber(attemptNumber)
                        .status(AttemptStatus.FAILURE)
                        .errorCode("USER_OPTED_OUT")
                        .errorMessage("User has opted out of " + message.getType() + " notifications")
                        .attemptedAt(Instant.now())
                        .build();
                deliveryAttemptRepository.save(attempt);

                notification.setStatus(NotificationStatus.CANCELLED);
                notificationRepository.save(notification);

                return DeliveryResult.failure("USER_OPTED_OUT", "User opted out of " + message.getType());
            }

            // 2. Destination Address Override
            if (message.getType() == NotificationType.EMAIL
                    && pref.getPreferredEmail() != null
                    && !pref.getPreferredEmail().isBlank()) {
                message.setRecipientAddress(pref.getPreferredEmail().trim());
            } else if (message.getType() == NotificationType.SMS
                    && pref.getPreferredPhone() != null
                    && !pref.getPreferredPhone().isBlank()) {
                message.setRecipientAddress(pref.getPreferredPhone().trim());
            }

            // 3. Timezone-Aware Quiet Hours Evaluation (Non-urgent notifications deferred until window ends)
            Instant now = Instant.now();
            if (!urgent && quietHoursEvaluator.isWithinQuietHours(pref, now)) {
                Instant quietHoursEnd = quietHoursEvaluator.calculateQuietHoursEnd(pref, now);
                log.info("Notification {} arrived during quiet hours in timezone {}. Deferring until {}",
                        notification.getId(), pref.getTimezone(), quietHoursEnd);

                DeliveryAttempt attempt = DeliveryAttempt.builder()
                        .notification(notification)
                        .attemptNumber(attemptNumber)
                        .status(AttemptStatus.RETRYING)
                        .nextRetryAt(quietHoursEnd)
                        .errorCode("QUIET_HOURS")
                        .errorMessage("Delivery deferred until quiet hours end at " + quietHoursEnd)
                        .attemptedAt(now)
                        .build();
                deliveryAttemptRepository.save(attempt);

                notification.setScheduledAt(quietHoursEnd);
                notificationRepository.save(notification);

                return DeliveryResult.failure("QUIET_HOURS", "Deferred until quiet hours end at " + quietHoursEnd);
            }
        }

        // Execute channel delivery strategy
        DeliveryStrategy strategy = strategyRegistry.getStrategy(message.getType());
        if (strategy == null) {
            log.error("No delivery strategy registered for channel type: {}", message.getType());
            return DeliveryResult.failure("UNSUPPORTED_CHANNEL", "No delivery strategy for " + message.getType());
        }
        DeliveryResult result = strategy.deliver(message);

        if (result.isSuccess()) {
            // Record successful delivery attempt
            DeliveryAttempt attempt = DeliveryAttempt.builder()
                    .notification(notification)
                    .attemptNumber(attemptNumber)
                    .status(AttemptStatus.SUCCESS)
                    .providerResponse(result.getProviderResponse())
                    .errorCode(result.getErrorCode())
                    .errorMessage(result.getErrorMessage())
                    .attemptedAt(Instant.now())
                    .build();
            deliveryAttemptRepository.save(attempt);

            notification.setStatus(NotificationStatus.DELIVERED);
            notification.setScheduledAt(null);
            notificationRepository.save(notification);

            notificationMetrics.recordNotificationDelivered(message.getType(), Duration.between(dispatchStartTime, Instant.now()));

            log.info("Notification {} transitioned to DELIVERED (attempt #{})",
                    notification.getId(), attemptNumber);
        } else {
            notificationMetrics.recordNotificationFailed(message.getType(), result.getErrorCode());

            // Evaluate retry eligibility via ErrorClassifier and max retry limit
            boolean isTransient = errorClassifier.isTransient(result.getErrorCode(), result.getErrorMessage());
            int maxAttempts = appProperties.getRabbitmq().getRetryMaxAttempts();

            if (isTransient && attemptNumber < maxAttempts) {
                Instant nextRetryAt = backoffCalculator.calculateNextRetry(attemptNumber);

                DeliveryAttempt attempt = DeliveryAttempt.builder()
                        .notification(notification)
                        .attemptNumber(attemptNumber)
                        .status(AttemptStatus.RETRYING)
                        .providerResponse(result.getProviderResponse())
                        .errorCode(result.getErrorCode())
                        .errorMessage(result.getErrorMessage())
                        .attemptedAt(Instant.now())
                        .nextRetryAt(nextRetryAt)
                        .build();
                deliveryAttemptRepository.save(attempt);

                // Remains PROCESSING scheduled for next retry
                notification.setScheduledAt(nextRetryAt);
                notificationRepository.save(notification);

                log.warn("Notification {} transient delivery failure on attempt #{}/{} (code: {}). Scheduled retry at {}",
                        notification.getId(), attemptNumber, maxAttempts, result.getErrorCode(), nextRetryAt);
            } else {
                // Permanent failure or retry limit exhausted -> Route to Dead Letter Queue
                DeliveryAttempt attempt = DeliveryAttempt.builder()
                        .notification(notification)
                        .attemptNumber(attemptNumber)
                        .status(AttemptStatus.FAILURE)
                        .providerResponse(result.getProviderResponse())
                        .errorCode(result.getErrorCode())
                        .errorMessage(result.getErrorMessage())
                        .attemptedAt(Instant.now())
                        .nextRetryAt(null)
                        .build();
                deliveryAttemptRepository.save(attempt);

                notification.setStatus(NotificationStatus.FAILED);
                notificationRepository.save(notification);

                // Route to RabbitMQ Dead Letter Queue
                dlqService.sendToDlq(message, attemptNumber, result.getErrorCode(), result.getErrorMessage());

                log.error("Notification {} delivery failed permanently or retries exhausted (attempt #{}/{}, code: {}). Routed to DLQ.",
                        notification.getId(), attemptNumber, maxAttempts, result.getErrorCode());
            }
        }

        return result;
    }

    private boolean isUrgent(NotificationMessage message) {
        if (message.getChannelMetadata() != null) {
            Object urgent = message.getChannelMetadata().get("urgent");
            if (Boolean.TRUE.equals(urgent) || "true".equalsIgnoreCase(String.valueOf(urgent))) {
                return true;
            }
            Object critical = message.getChannelMetadata().get("critical");
            if (Boolean.TRUE.equals(critical) || "true".equalsIgnoreCase(String.valueOf(critical))) {
                return true;
            }
            Object priority = message.getChannelMetadata().get("priority");
            if ("HIGH".equalsIgnoreCase(String.valueOf(priority)) || "URGENT".equalsIgnoreCase(String.valueOf(priority))) {
                return true;
            }
        }
        if (message.getSubject() != null) {
            String sub = message.getSubject().toUpperCase();
            if (sub.contains("OTP") || sub.contains("2FA") || sub.contains("VERIFICATION") || sub.contains("SECURITY ALERT")) {
                return true;
            }
        }
        return false;
    }
}
