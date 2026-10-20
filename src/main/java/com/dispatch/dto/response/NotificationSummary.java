package com.dispatch.dto.response;

import com.dispatch.entity.enums.NotificationStatus;
import com.dispatch.entity.enums.NotificationType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * Lightweight notification summary for list / history endpoints.
 */
@Data
@Builder
public class NotificationSummary {

    private UUID id;
    private NotificationType type;
    private NotificationStatus status;
    private String subject;
    private String recipientAddress;
    private Instant scheduledAt;
    private Instant createdAt;
    private Instant updatedAt;
}
