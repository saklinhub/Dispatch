package com.dispatch.resilience;

import com.dispatch.dto.message.NotificationMessage;
import com.dispatch.dto.response.DlqStatsResponse;
import com.dispatch.dto.response.NotificationResponse;

import java.util.UUID;

/**
 * Service managing Dead Letter Queue (DLQ) operations, message routing, inspection, and replay.
 */
public interface DlqService {

    /**
     * Routes a failed notification to the Dead Letter Queue exchange with failure metadata headers.
     *
     * @param message       the notification message
     * @param attemptNumber the total number of attempts made
     * @param errorCode     the failure error code
     * @param errorMessage  the descriptive failure message
     */
    void sendToDlq(NotificationMessage message, int attemptNumber, String errorCode, String errorMessage);

    /**
     * Manually replays a failed notification, resetting its status to PENDING
     * and writing a new outbox event for re-publishing.
     *
     * @param notificationId ID of the failed notification
     * @return updated notification details
     */
    NotificationResponse replayNotification(UUID notificationId);

    /**
     * Inspects DLQ status, returning current message counts in RabbitMQ and total failed notifications.
     */
    DlqStatsResponse getDlqStats();
}
