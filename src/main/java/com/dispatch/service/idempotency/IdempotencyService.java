package com.dispatch.service.idempotency;

import com.dispatch.dto.request.CreateNotificationRequest;
import com.dispatch.dto.response.IdempotentExecutionResult;
import com.dispatch.dto.response.IdempotencyResponse;
import com.dispatch.dto.response.NotificationResponse;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Service managing distributed request idempotency, concurrency deduplication,
 * payload fingerprinting, and cached response replay.
 */
public interface IdempotencyService {

    /**
     * Computes a deterministic SHA-256 fingerprint of the request payload and target user.
     */
    String computeHash(UUID userId, CreateNotificationRequest request);

    /**
     * Executes the supplied action idempotently.
     * If key is null or blank, executes without idempotency controls.
     * If key was already completed with matching payload, returns cached response.
     * If key is in-flight, throws IdempotencyConflictException (409 Conflict).
     * If key was previously used with a different payload, throws IdempotencyConflictException (409 Conflict).
     */
    IdempotentExecutionResult<NotificationResponse> execute(
            String key,
            UUID userId,
            CreateNotificationRequest request,
            Supplier<NotificationResponse> action);

    /**
     * Retrieves an idempotency record by key for inspection.
     */
    Optional<IdempotencyResponse> getRecord(String key);

    /**
     * Manually deletes an idempotency key (allowing re-submission).
     */
    void deleteRecord(String key);

    /**
     * Purges expired idempotency records older than retention threshold.
     */
    int cleanupExpiredRecords();
}
