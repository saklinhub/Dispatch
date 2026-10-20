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
}
