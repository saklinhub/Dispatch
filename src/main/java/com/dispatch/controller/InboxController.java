package com.dispatch.controller;

import com.dispatch.dto.response.InboxMessageResponse;
import com.dispatch.dto.response.PageResponse;
import com.dispatch.dto.response.UnreadCountResponse;
import com.dispatch.service.InboxService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * REST controller for managing user in-app inbox messages.
 */
@RestController
@RequestMapping("/api/v1/users/{userId}/inbox")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "In-App Inbox", description = "Query, read, and manage in-app notifications and inbox messages")
public class InboxController {

    private final InboxService inboxService;

    @GetMapping
    @Operation(
            summary = "Get user inbox messages",
            description = "Retrieves paginated in-app inbox messages for a user, with optional read status filter."
    )
    public ResponseEntity<PageResponse<InboxMessageResponse>> getInbox(
            @PathVariable UUID userId,
            @Parameter(description = "Optional filter for read status: true, false, or omitted for all")
            @RequestParam(required = false) Boolean read,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(inboxService.getInbox(userId, read, pageable));
    }

    @GetMapping("/unread-count")
    @Operation(
            summary = "Get unread message count badge",
            description = "Returns the total number of unread in-app messages for the user."
    )
    public ResponseEntity<UnreadCountResponse> getUnreadCount(@PathVariable UUID userId) {
        return ResponseEntity.ok(inboxService.getUnreadCount(userId));
    }

    @PatchMapping("/{messageId}/read")
    @Operation(
            summary = "Mark inbox message as read",
            description = "Marks a specific in-app message as read and timestamps readAt."
    )
    public ResponseEntity<InboxMessageResponse> markAsRead(
            @PathVariable UUID userId,
            @PathVariable UUID messageId) {
        return ResponseEntity.ok(inboxService.markAsRead(userId, messageId));
    }

    @PatchMapping("/{messageId}/unread")
    @Operation(
            summary = "Mark inbox message as unread",
            description = "Reverts a specific in-app message back to unread status and clears readAt."
    )
    public ResponseEntity<InboxMessageResponse> markAsUnread(
            @PathVariable UUID userId,
            @PathVariable UUID messageId) {
        return ResponseEntity.ok(inboxService.markAsUnread(userId, messageId));
    }

    @RequestMapping(value = "/read-all", method = {RequestMethod.POST, RequestMethod.PATCH})
    @Operation(
            summary = "Bulk mark all inbox messages as read",
            description = "Marks all unread messages in the user's inbox as read in a single batch operation."
    )
    public ResponseEntity<Map<String, Object>> markAllAsRead(@PathVariable UUID userId) {
        int count = inboxService.markAllAsRead(userId);
        return ResponseEntity.ok(Map.of(
                "userId", userId,
                "updatedCount", count,
                "message", "Marked " + count + " message(s) as read"
        ));
    }

    @DeleteMapping("/{messageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Delete an inbox message",
            description = "Permanently deletes a specific inbox message belonging to the user."
    )
    public ResponseEntity<Void> deleteMessage(
            @PathVariable UUID userId,
            @PathVariable UUID messageId) {
        inboxService.deleteMessage(userId, messageId);
        return ResponseEntity.noContent().build();
    }
}
