package com.dispatch.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * Consistent error response body returned by the API on all 4xx / 5xx errors.
 */
@Data
@Builder
public class ApiError {

    private int status;
    private String error;
    private String message;
    private String path;
    private Instant timestamp;

    /** Field-level validation errors; present only on 400 responses. */
    private List<FieldError> fieldErrors;

    @Data
    @Builder
    public static class FieldError {
        private String field;
        private Object rejectedValue;
        private String message;
    }
}
