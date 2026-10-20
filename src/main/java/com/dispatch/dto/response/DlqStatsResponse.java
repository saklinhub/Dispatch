package com.dispatch.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Statistics and health metrics for the Dead Letter Queue.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DlqStatsResponse {

    private String queueName;
    private long dlqMessageCount;
    private long totalFailedNotifications;
}
