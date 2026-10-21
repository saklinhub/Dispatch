package com.dispatch.entity.enums;

/**
 * Lifecycle states of an idempotent request.
 */
public enum IdempotencyStatus {
    /**
     * Request is currently being processed by a worker thread.
     */
    IN_PROGRESS,

    /**
     * Request has completed successfully; response payload is cached for replay.
     */
    COMPLETED,

    /**
     * Request execution failed with an unhandled error; key can be retried.
     */
    FAILED
}
