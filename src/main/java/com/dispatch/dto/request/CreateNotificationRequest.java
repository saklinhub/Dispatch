package com.dispatch.dto.request;

import com.dispatch.entity.enums.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * Request body for creating a new notification.
 */
@Data
public class CreateNotificationRequest {

    @NotNull(message = "Notification type is required")
    private NotificationType type;

    @NotBlank(message = "Recipient address is required")
    @Size(max = 255, message = "Recipient address must be at most 255 characters")
    private String recipientAddress;

    @Size(max = 500, message = "Subject must be at most 500 characters")
    private String subject;

    @NotBlank(message = "Body is required")
    private String body;

    /**
     * Optional caller-supplied idempotency key.
     * Duplicate requests with the same key return the existing notification.
     */
    @Size(max = 255, message = "Idempotency key must be at most 255 characters")
    private String idempotencyKey;

    /**
     * Optional channel-specific extras — e.g. templateId, Twilio messagingServiceSid,
     * variable substitution map.
     */
    private Map<String, Object> channelMetadata;

    /**
     * Optional future delivery time. Null means process immediately.
     */
    private Instant scheduledAt;
}
