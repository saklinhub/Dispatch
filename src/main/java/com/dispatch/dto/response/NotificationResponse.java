package com.dispatch.dto.response;

import com.dispatch.entity.enums.NotificationStatus;
import com.dispatch.entity.enums.NotificationType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Full notification detail response, including delivery attempt history.
 */
@Data
@Builder
public class NotificationResponse {

    private UUID id;
    private UUID userId;
    private NotificationType type;
    private NotificationStatus status;
    private String subject;
    private String body;
    private String recipientAddress;
    private String idempotencyKey;
    private Map<String, Object> channelMetadata;
    private Instant scheduledAt;
    private Instant createdAt;
    private Instant updatedAt;
    private List<DeliveryAttemptResponse> deliveryAttempts;
}
