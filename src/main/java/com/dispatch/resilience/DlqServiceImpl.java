package com.dispatch.resilience;

import com.dispatch.config.AppProperties;
import com.dispatch.config.RabbitMQConfig;
import com.dispatch.dto.message.NotificationMessage;
import com.dispatch.dto.response.DlqStatsResponse;
import com.dispatch.dto.response.NotificationResponse;
import com.dispatch.entity.Notification;
import com.dispatch.entity.OutboxEvent;
import com.dispatch.entity.enums.NotificationStatus;
import com.dispatch.entity.enums.OutboxEventStatus;
import com.dispatch.exception.InvalidStateException;
import com.dispatch.exception.ResourceNotFoundException;
import com.dispatch.mapper.NotificationMapper;
import com.dispatch.repository.NotificationRepository;
import com.dispatch.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/**
 * Implementation of DLQ routing, stat retrieval, and notification replay.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DlqServiceImpl implements DlqService {

    private final RabbitTemplate rabbitTemplate;
    private final RabbitAdmin rabbitAdmin;
    private final AppProperties appProperties;
    private final NotificationRepository notificationRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final NotificationMapper notificationMapper;
    private final ErrorClassifier errorClassifier;

    @Override
    public void sendToDlq(NotificationMessage message, int attemptNumber, String errorCode, String errorMessage) {
        try {
            boolean isPermanent = errorClassifier.isPermanent(errorCode, errorMessage);
            String deathReason = isPermanent ? "PERMANENT_FAILURE" : "RETRIES_EXHAUSTED";

            rabbitTemplate.convertAndSend(
                    appProperties.getRabbitmq().getDlqExchange(),
                    RabbitMQConfig.DLQ_ROUTING_KEY,
                    message,
                    m -> {
                        MessageProperties props = m.getMessageProperties();
                        props.setMessageId(message.getNotificationId() != null
                                ? message.getNotificationId().toString()
                                : UUID.randomUUID().toString());
                        props.setHeader("x-death-reason", deathReason);
                        props.setHeader("x-error-code", errorCode != null ? errorCode : "UNKNOWN");
                        props.setHeader("x-error-message", errorMessage != null ? errorMessage : "Delivery failed");
                        props.setHeader("x-attempt-count", attemptNumber);
                        props.setHeader("x-failed-at", Instant.now().toString());
                        props.setTimestamp(Date.from(Instant.now()));
                        return m;
                    }
            );

            log.warn("Routed notification {} to DLQ: [reason={}, attempts={}, code={}, msg={}]",
                    message.getNotificationId(), deathReason, attemptNumber, errorCode, errorMessage);
        } catch (Exception ex) {
            log.error("Failed to publish notification {} to DLQ: {}", message.getNotificationId(), ex.getMessage(), ex);
        }
    }

    @Override
    @Transactional
    public NotificationResponse replayNotification(UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> ResourceNotFoundException.notification(notificationId.toString()));

        if (notification.getStatus() != NotificationStatus.FAILED) {
            throw new InvalidStateException("Only FAILED notifications can be replayed. Current status: " + notification.getStatus());
        }

        notification.setStatus(NotificationStatus.PENDING);
        notification.setScheduledAt(null);
        notification = notificationRepository.save(notification);

        Map<String, Object> payload = new HashMap<>();
        payload.put("notificationId", notification.getId().toString());
        payload.put("userId", notification.getUser().getId().toString());
        payload.put("type", notification.getType().name());
        payload.put("recipientAddress", notification.getRecipientAddress());
        payload.put("subject", notification.getSubject());
        payload.put("body", notification.getBody());
        if (notification.getIdempotencyKey() != null) {
            payload.put("idempotencyKey", notification.getIdempotencyKey());
        }
        if (notification.getChannelMetadata() != null) {
            payload.put("channelMetadata", notification.getChannelMetadata());
        }

        OutboxEvent outboxEvent = OutboxEvent.builder()
                .aggregateType("NOTIFICATION")
                .aggregateId(notification.getId())
                .eventType("NOTIFICATION_REPLAYED")
                .payload(payload)
                .status(OutboxEventStatus.PENDING)
                .build();

        outboxEventRepository.save(outboxEvent);

        log.info("Replayed notification {} and queued outbox event {}", notification.getId(), outboxEvent.getId());
        return notificationMapper.toResponse(notification);
    }

    @Override
    @Transactional(readOnly = true)
    public DlqStatsResponse getDlqStats() {
        String queueName = appProperties.getRabbitmq().getDlqQueue();
        long dlqCount = 0;
        try {
            Properties props = rabbitAdmin.getQueueProperties(queueName);
            if (props != null && props.containsKey(RabbitAdmin.QUEUE_MESSAGE_COUNT)) {
                Object count = props.get(RabbitAdmin.QUEUE_MESSAGE_COUNT);
                if (count instanceof Number number) {
                    dlqCount = number.longValue();
                }
            }
        } catch (Exception ex) {
            log.warn("Could not retrieve DLQ queue properties from broker: {}", ex.getMessage());
        }

        long failedCount = notificationRepository.countByStatus(NotificationStatus.FAILED);
        return DlqStatsResponse.builder()
                .queueName(queueName)
                .dlqMessageCount(dlqCount)
                .totalFailedNotifications(failedCount)
                .build();
    }
}
