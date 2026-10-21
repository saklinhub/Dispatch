package com.dispatch.dto.response;

import com.dispatch.entity.enums.IdempotencyStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Inspection view of an idempotency record.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyResponse {

    private UUID id;
    private String idempotencyKey;
    private String requestHash;
    private IdempotencyStatus status;
    private Integer responseStatusCode;
    private UUID notificationId;
    private Instant createdAt;
    private Instant lockedUntil;
}
