package com.dispatch.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for an in-app inbox message.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InboxMessageResponse {

    private UUID id;
    private UUID userId;
    private UUID notificationId;
    private String title;
    private String body;
    private boolean read;
    private Instant readAt;
    private Instant createdAt;
}
