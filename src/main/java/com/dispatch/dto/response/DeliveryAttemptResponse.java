package com.dispatch.dto.response;

import com.dispatch.entity.enums.AttemptStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for a single delivery attempt record.
 */
@Data
@Builder
public class DeliveryAttemptResponse {

    private UUID id;
    private int attemptNumber;
    private AttemptStatus status;
    private String providerResponse;
    private String errorCode;
    private String errorMessage;
    private Instant attemptedAt;
    private Instant nextRetryAt;
    private Instant createdAt;
}
