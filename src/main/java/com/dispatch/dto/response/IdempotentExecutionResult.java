package com.dispatch.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Encapsulates the execution result of an idempotent operation,
 * indicating whether the response was newly created or replayed from cache.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdempotentExecutionResult<T> {

    private T data;
    private boolean replayed;
    private int statusCode;
}
