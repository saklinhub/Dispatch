package com.dispatch.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Transactional outbox queue metrics")
public class OutboxStatsResponse {

    @Schema(description = "Total pending events awaiting publication")
    private long pendingCount;

    @Schema(description = "Total successfully published events")
    private long publishedCount;

    @Schema(description = "Total failed events")
    private long failedCount;

    @Schema(description = "Total recorded outbox events")
    private long totalCount;
}
