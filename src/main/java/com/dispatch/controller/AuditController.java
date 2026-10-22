package com.dispatch.controller;

import com.dispatch.dto.response.DeliveryAttemptResponse;
import com.dispatch.dto.response.PageResponse;
import com.dispatch.entity.enums.AttemptStatus;
import com.dispatch.service.DeliveryAuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for inspecting delivery attempts and audit history.
 */
@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Delivery Audit", description = "Query delivery audit history and attempt details")
public class AuditController {

    private final DeliveryAuditService auditService;

    @GetMapping("/api/v1/users/{userId}/delivery-history")
    @Operation(
            summary = "Get user delivery history",
            description = "Retrieves paginated delivery attempt audit history for all notifications sent to a user, with optional status filter."
    )
    public ResponseEntity<PageResponse<DeliveryAttemptResponse>> getUserDeliveryHistory(
            @PathVariable UUID userId,
            @Parameter(description = "Optional filter by attempt status (e.g. DELIVERED, FAILED, RETRYING)")
            @RequestParam(required = false) AttemptStatus status,
            @PageableDefault(size = 20, sort = "attemptedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(auditService.getUserDeliveryHistory(userId, status, pageable));
    }

    @GetMapping("/api/v1/audit/attempts/{attemptId}")
    @Operation(
            summary = "Get delivery attempt details",
            description = "Retrieves diagnostic details, provider responses, and error information for a single delivery attempt."
    )
    public ResponseEntity<DeliveryAttemptResponse> getAttemptById(@PathVariable UUID attemptId) {
        return ResponseEntity.ok(auditService.getAttemptById(attemptId));
    }
}
