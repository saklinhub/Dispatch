package com.dispatch.controller;

import com.dispatch.dto.response.DlqStatsResponse;
import com.dispatch.dto.response.NotificationResponse;
import com.dispatch.resilience.DlqService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for Dead Letter Queue (DLQ) monitoring and replaying failed messages.
 */
@RestController
@RequestMapping("/api/v1/dlq")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Dead Letter Queue (DLQ)", description = "Inspect DLQ metrics and replay failed or dead-lettered notifications")
public class DlqController {

    private final DlqService dlqService;

    @GetMapping("/stats")
    @Operation(
            summary = "Get Dead Letter Queue statistics",
            description = "Returns current message counts in RabbitMQ DLQ queue and count of failed notifications in the database."
    )
    public ResponseEntity<DlqStatsResponse> getStats() {
        return ResponseEntity.ok(dlqService.getDlqStats());
    }

    @PostMapping("/replay/{notificationId}")
    @Operation(
            summary = "Replay dead-lettered notification",
            description = "Resets a FAILED notification back to PENDING and publishes an outbox event for re-delivery."
    )
    public ResponseEntity<NotificationResponse> replayNotification(@PathVariable UUID notificationId) {
        return ResponseEntity.ok(dlqService.replayNotification(notificationId));
    }
}
