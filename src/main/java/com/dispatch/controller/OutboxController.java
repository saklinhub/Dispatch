package com.dispatch.controller;

import com.dispatch.dto.response.OutboxEventResponse;
import com.dispatch.dto.response.OutboxStatsResponse;
import com.dispatch.dto.response.PageResponse;
import com.dispatch.entity.enums.OutboxEventStatus;
import com.dispatch.service.outbox.OutboxService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/outbox")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Transactional Outbox", description = "Monitor and manage transactional outbox events and broker relay")
public class OutboxController {

    private final OutboxService outboxService;

    @GetMapping("/events")
    @Operation(summary = "List outbox events", description = "Returns a paginated list of outbox events, optionally filtered by status.")
    public ResponseEntity<PageResponse<OutboxEventResponse>> listEvents(
            @Parameter(description = "Filter by outbox status") @RequestParam(required = false) OutboxEventStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        return ResponseEntity.ok(outboxService.listEvents(status, PageRequest.of(page, size, sort)));
    }

    @GetMapping("/stats")
    @Operation(summary = "Get outbox statistics", description = "Returns summary counts of pending, published, and failed outbox events.")
    public ResponseEntity<OutboxStatsResponse> getStats() {
        return ResponseEntity.ok(outboxService.getStats());
    }

    @PostMapping("/events/{id}/retry")
    @Operation(summary = "Retry outbox event", description = "Manually re-triggers publishing of an outbox event to RabbitMQ.")
    public ResponseEntity<OutboxEventResponse> retryEvent(@PathVariable UUID id) {
        return ResponseEntity.ok(outboxService.retryEvent(id));
    }
}
