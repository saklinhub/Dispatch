package com.dispatch.dto.response;

import com.dispatch.entity.enums.AttemptStatus;
import com.dispatch.entity.enums.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for a single delivery attempt record.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryAttemptResponse {

    private UUID id;
    private UUID notificationId;
    private NotificationType channelType;
    private int attemptNumber;
    private AttemptStatus status;
    private String providerResponse;
    private String errorCode;
    private String errorMessage;
    private Instant attemptedAt;
    private Instant nextRetryAt;
    private Instant createdAt;
}
