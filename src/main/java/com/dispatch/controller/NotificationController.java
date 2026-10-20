package com.dispatch.controller;

import com.dispatch.dto.request.CreateNotificationRequest;
import com.dispatch.dto.response.ApiError;
import com.dispatch.dto.response.DeliveryAttemptResponse;
import com.dispatch.dto.response.NotificationResponse;
import com.dispatch.dto.response.NotificationSummary;
import com.dispatch.dto.response.PageResponse;
import com.dispatch.entity.enums.NotificationStatus;
import com.dispatch.entity.enums.NotificationType;
import com.dispatch.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for notification CRUD operations.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Create, query and manage notifications")
public class NotificationController {

    private final NotificationService notificationService;

    //POST /api/v1/users/{userId}/notifications

    @PostMapping("/users/{userId}/notifications")
    @Operation(
        summary = "Create a notification",
        description = "Creates a new notification for the given user. " +
                      "Supplying an idempotency key makes the request safe to retry.",
        responses = {
            @ApiResponse(responseCode = "201", description = "Notification created"),
            @ApiResponse(responseCode = "400", description = "Validation error",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "User not found",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
        }
    )
    public ResponseEntity<NotificationResponse> createNotification(
            @PathVariable UUID userId,
            @Valid @RequestBody CreateNotificationRequest request) {

        NotificationResponse response = notificationService.createNotification(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    //GET /api/v1/notifications/{id}

    @GetMapping("/notifications/{id}")
    @Operation(
        summary = "Get a notification",
        description = "Returns full notification detail including all delivery attempt history."
    )
    public ResponseEntity<NotificationResponse> getNotification(@PathVariable UUID id) {
        return ResponseEntity.ok(notificationService.getNotification(id));
    }

    //GET /api/v1/notifications/{id}/status

    @GetMapping("/notifications/{id}/status")
    @Operation(summary = "Get notification status", description = "Lightweight status-only lookup.")
    public ResponseEntity<NotificationStatus> getNotificationStatus(@PathVariable UUID id) {
        NotificationResponse n = notificationService.getNotification(id);
        return ResponseEntity.ok(n.getStatus());
    }

    //GET /api/v1/users/{userId}/notifications

    @GetMapping("/users/{userId}/notifications")
    @Operation(
        summary = "List notifications for a user",
        description = "Returns a paginated list. Optionally filter by status or channel type."
    )
    public ResponseEntity<PageResponse<NotificationSummary>> listNotifications(
            @PathVariable UUID userId,
            @Parameter(description = "Filter by status") @RequestParam(required = false) NotificationStatus status,
            @Parameter(description = "Filter by channel type") @RequestParam(required = false) NotificationType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();

        PageResponse<NotificationSummary> response = notificationService.listNotifications(
                userId, status, type, PageRequest.of(page, size, sort));

        return ResponseEntity.ok(response);
    }

    //DELETE /api/v1/notifications/{id}

    @DeleteMapping("/notifications/{id}")
    @Operation(
        summary = "Cancel a notification",
        description = "Cancels a PENDING or PROCESSING notification. " +
                      "Returns 422 if the notification has already been delivered or cancelled."
    )
    public ResponseEntity<NotificationResponse> cancelNotification(@PathVariable UUID id) {
        return ResponseEntity.ok(notificationService.cancelNotification(id));
    }

    //GET /api/v1/notifications/{id}/attempts

    @GetMapping("/notifications/{id}/attempts")
    @Operation(
        summary = "Get delivery attempts",
        description = "Returns all delivery attempt records for a notification, " +
                      "ordered by attempt number ascending."
    )
    public ResponseEntity<List<DeliveryAttemptResponse>> getDeliveryAttempts(@PathVariable UUID id) {
        return ResponseEntity.ok(notificationService.getDeliveryAttempts(id));
    }

    //POST /api/v1/notifications/{id}/replay

    @PostMapping("/notifications/{id}/replay")
    @Operation(
        summary = "Replay a failed notification",
        description = "Resets a FAILED notification back to PENDING and triggers re-publishing via the Transactional Outbox. " +
                      "Returns 422 if the notification is not in FAILED state."
    )
    public ResponseEntity<NotificationResponse> replayNotification(@PathVariable UUID id) {
        return ResponseEntity.ok(notificationService.replayNotification(id));
    }
}
