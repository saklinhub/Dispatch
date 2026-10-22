package com.dispatch.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a requested resource cannot be found.
 * Maps to HTTP 404.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException notification(String id) {
        return new ResourceNotFoundException("Notification not found with id: " + id);
    }

    public static ResourceNotFoundException user(String id) {
        return new ResourceNotFoundException("User not found with id: " + id);
    }

    public static ResourceNotFoundException outboxEvent(String id) {
        return new ResourceNotFoundException("Outbox event not found with id: " + id);
    }

    public static ResourceNotFoundException inboxMessage(String id) {
        return new ResourceNotFoundException("Inbox message not found with id: " + id);
    }

    public static ResourceNotFoundException deliveryAttempt(String id) {
        return new ResourceNotFoundException("Delivery attempt not found with id: " + id);
    }
}
