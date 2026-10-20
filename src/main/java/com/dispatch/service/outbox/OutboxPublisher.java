package com.dispatch.service.outbox;

import com.dispatch.config.AppProperties;
import com.dispatch.dto.message.NotificationMessage;
import com.dispatch.entity.OutboxEvent;
import com.dispatch.entity.enums.NotificationType;
import com.dispatch.entity.enums.OutboxEventStatus;
import com.dispatch.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes individual outbox events to RabbitMQ and records publishing status.
 * Runs in its own transaction (REQUIRES_NEW) to isolate event publication outcomes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final OutboxEventRepository outboxEventRepository;
    private final AppProperties appProperties;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean publish(OutboxEvent event) {
        try {
            Map<String, Object> payload = event.getPayload();

            if ("NOTIFICATION".equalsIgnoreCase(event.getAggregateType())) {
                NotificationMessage message = buildNotificationMessage(event, payload);

                // Publish to main notification processing exchange
                rabbitTemplate.convertAndSend(
                        appProperties.getRabbitmq().getNotificationExchange(),
                        appProperties.getRabbitmq().getNotificationRoutingKey(),
                        message,
                        m -> {
                            m.getMessageProperties().setMessageId(event.getId().toString());
                            m.getMessageProperties().setCorrelationId(event.getAggregateId().toString());
                            m.getMessageProperties().setTimestamp(java.util.Date.from(Instant.now()));
                            return m;
                        }
                );

                // Also publish outbox event to topic exchange for domain event subscribers
                rabbitTemplate.convertAndSend(
                        appProperties.getRabbitmq().getOutboxExchange(),
                        appProperties.getRabbitmq().getOutboxRoutingKey(),
                        event.getPayload(),
                        m -> {
                            m.getMessageProperties().setMessageId(event.getId().toString());
                            m.getMessageProperties().setCorrelationId(event.getAggregateId().toString());
                            return m;
                        }
                );
            } else {
                // Generic publish to outbox topic exchange
                rabbitTemplate.convertAndSend(
                        appProperties.getRabbitmq().getOutboxExchange(),
                        appProperties.getRabbitmq().getOutboxRoutingKey(),
                        event.getPayload()
                );
            }

            event.setStatus(OutboxEventStatus.PUBLISHED);
            event.setPublishedAt(Instant.now());
            event.setErrorMessage(null);
            outboxEventRepository.save(event);

            log.info("Successfully published outbox event {} for aggregate {}",
                    event.getId(), event.getAggregateId());
            return true;

        } catch (Exception ex) {
            log.error("Failed to publish outbox event {}: {}", event.getId(), ex.getMessage(), ex);
            event.setStatus(OutboxEventStatus.FAILED);
            event.setErrorMessage(ex.getMessage());
            outboxEventRepository.save(event);
            return false;
        }
    }

    private NotificationMessage buildNotificationMessage(OutboxEvent event, Map<String, Object> payload) {
        UUID notificationId = event.getAggregateId();
        UUID userId = payload.containsKey("userId") ? UUID.fromString(payload.get("userId").toString()) : null;
        NotificationType type = payload.containsKey("type")
                ? NotificationType.valueOf(payload.get("type").toString())
                : null;
        String recipient = (String) payload.get("recipientAddress");
        String subject = (String) payload.get("subject");
        String body = (String) payload.get("body");
        String idempotencyKey = (String) payload.get("idempotencyKey");

        @SuppressWarnings("unchecked")
        Map<String, Object> channelMetadata = (Map<String, Object>) payload.get("channelMetadata");

        Instant scheduledAt = payload.get("scheduledAt") != null
                ? Instant.parse(payload.get("scheduledAt").toString())
                : null;
        Instant createdAt = event.getCreatedAt() != null ? event.getCreatedAt() : Instant.now();

        return NotificationMessage.builder()
                .eventId(event.getId())
                .notificationId(notificationId)
                .userId(userId)
                .type(type)
                .recipientAddress(recipient)
                .subject(subject)
                .body(body)
                .idempotencyKey(idempotencyKey)
                .channelMetadata(channelMetadata)
                .scheduledAt(scheduledAt)
                .createdAt(createdAt)
                .attemptCount(0)
                .build();
    }
}
