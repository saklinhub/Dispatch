package com.dispatch.dto.response;

import com.dispatch.entity.enums.OutboxEventStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Transactional outbox event audit record")
public class OutboxEventResponse {

    @Schema(description = "Unique event ID")
    private UUID id;

    @Schema(description = "Aggregate type (e.g. NOTIFICATION)")
    private String aggregateType;

    @Schema(description = "Aggregate ID")
    private UUID aggregateId;

    @Schema(description = "Event type name")
    private String eventType;

    @Schema(description = "JSON event payload")
    private Map<String, Object> payload;

    @Schema(description = "Current status")
    private OutboxEventStatus status;

    @Schema(description = "Error message if publication failed")
    private String errorMessage;

    @Schema(description = "Creation timestamp")
    private Instant createdAt;

    @Schema(description = "Publication timestamp")
    private Instant publishedAt;
}
