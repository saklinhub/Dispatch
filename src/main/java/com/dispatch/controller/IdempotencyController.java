package com.dispatch.controller;

import com.dispatch.dto.response.IdempotencyResponse;
import com.dispatch.exception.ResourceNotFoundException;
import com.dispatch.service.idempotency.IdempotencyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for inspecting and managing idempotency records.
 */
@RestController
@RequestMapping("/api/v1/idempotency")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Idempotency", description = "Inspect and manage idempotency keys and cached request fingerprints")
public class IdempotencyController {

    private final IdempotencyService idempotencyService;

    @GetMapping("/{key}")
    @Operation(
            summary = "Get idempotency record",
            description = "Inspect the status, request fingerprint hash, and lock timing of an idempotency key."
    )
    public ResponseEntity<IdempotencyResponse> getRecord(@PathVariable String key) {
        return idempotencyService.getRecord(key)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("Idempotency key '" + key + "' not found"));
    }

    @DeleteMapping("/{key}")
    @Operation(
            summary = "Delete idempotency record",
            description = "Manually clears an idempotency key, allowing re-submission."
    )
    public ResponseEntity<Void> deleteRecord(@PathVariable String key) {
        idempotencyService.deleteRecord(key);
        return ResponseEntity.noContent().build();
    }
}
