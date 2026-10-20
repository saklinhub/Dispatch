package com.dispatch.resilience;

/**
 * Categorization of notification delivery failure errors.
 */
public enum ErrorType {
    /**
     * Transient / temporary error (rate limit, timeout, temporary network error, SMTP 4xx).
     * Eligible for retry with exponential backoff.
     */
    TRANSIENT,

    /**
     * Permanent / terminal error (invalid number, mailbox does not exist, SMTP 5xx, invalid credentials).
     * Not eligible for retry; immediately routed to Dead Letter Queue.
     */
    PERMANENT
}
