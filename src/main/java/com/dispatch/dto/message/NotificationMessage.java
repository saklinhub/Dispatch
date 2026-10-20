package com.dispatch.dto.message;

import com.dispatch.entity.enums.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Message contract published to RabbitMQ for asynchronous notification delivery.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationMessage implements Serializable {

    private UUID eventId;
    private UUID notificationId;
    private UUID userId;
    private NotificationType type;
    private String recipientAddress;
    private String subject;
    private String body;
    private String idempotencyKey;
    private Map<String, Object> channelMetadata;
    private Instant scheduledAt;
    private Instant createdAt;

    @Builder.Default
    private int attemptCount = 0;
}
