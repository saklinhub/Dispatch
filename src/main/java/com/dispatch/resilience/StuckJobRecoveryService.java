package com.dispatch.resilience;

import com.dispatch.config.AppProperties;
import com.dispatch.dto.message.NotificationMessage;
import com.dispatch.entity.Notification;
import com.dispatch.entity.enums.NotificationStatus;
import com.dispatch.repository.DeliveryAttemptRepository;
import com.dispatch.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Background recovery worker detecting and recovering notifications stuck in
 * PROCESSING state beyond the configured threshold duration.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StuckJobRecoveryService {

    private final NotificationRepository notificationRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final RabbitTemplate rabbitTemplate;
    private final DlqService dlqService;
    private final AppProperties appProperties;

    @Scheduled(fixedDelayString = "${dispatch.recovery.check-interval-ms:60000}")
    @Transactional
    public int recoverStuckJobs() {
        int thresholdMinutes = appProperties.getRecovery().getStuckThresholdMinutes();
        Instant threshold = Instant.now().minus(Duration.ofMinutes(thresholdMinutes));

        List<Notification> stuckNotifications = notificationRepository.findStuckProcessingNotifications(threshold);

        if (stuckNotifications.isEmpty()) {
            return 0;
        }

        log.warn("Found {} notifications stuck in PROCESSING state longer than {} minutes",
                stuckNotifications.size(), thresholdMinutes);

        int recoveredCount = 0;
        int maxAttempts = appProperties.getRabbitmq().getRetryMaxAttempts();

        for (Notification notification : stuckNotifications) {
            long attemptCount = deliveryAttemptRepository.countByNotificationId(notification.getId());

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
                    .attemptCount((int) attemptCount)
                    .build();

            if (attemptCount < maxAttempts) {
                // Reset updatedAt so it doesn't repeatedly trigger immediately
                notification.setUpdatedAt(Instant.now());
                notificationRepository.save(notification);

                try {
                    rabbitTemplate.convertAndSend(
                            appProperties.getRabbitmq().getNotificationExchange(),
                            appProperties.getRabbitmq().getNotificationRoutingKey(),
                            message
                    );
                    recoveredCount++;
                    log.info("Recovered stuck notification {} by re-enqueuing to RabbitMQ", notification.getId());
                } catch (Exception ex) {
                    log.error("Failed to re-enqueue stuck notification {}: {}", notification.getId(), ex.getMessage(), ex);
                }
            } else {
                // Max attempts already reached -> transition to FAILED and route to DLQ
                notification.setStatus(NotificationStatus.FAILED);
                notificationRepository.save(notification);

                dlqService.sendToDlq(message, (int) attemptCount, "STUCK_PROCESSING_TIMEOUT",
                        "Notification stuck in PROCESSING state beyond threshold");

                log.warn("Stuck notification {} exceeded max retry attempts ({}). Marked FAILED and routed to DLQ.",
                        notification.getId(), attemptCount);
            }
        }

        return recoveredCount;
    }
}
