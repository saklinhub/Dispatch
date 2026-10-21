package com.dispatch.exception;

/**
 * Thrown when an idempotency conflict occurs (e.g. payload mismatch or concurrent in-flight execution).
 * Results in HTTP 409 Conflict.
 */
public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String message) {
        super(message);
    }
}
