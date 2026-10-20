package com.dispatch.service;

import com.dispatch.dto.request.CreateNotificationRequest;
import com.dispatch.dto.response.DeliveryAttemptResponse;
import com.dispatch.dto.response.NotificationResponse;
import com.dispatch.dto.response.NotificationSummary;
import com.dispatch.dto.response.PageResponse;
import com.dispatch.entity.enums.NotificationStatus;
import com.dispatch.entity.enums.NotificationType;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/**
 * Core notification management operations exposed by the REST API.
 */
public interface NotificationService {

    /**
     * Create and persist a new notification for the given user.
     * If an idempotency key is present and matches an existing record,
     * that record is returned without creating a duplicate.
     */
    NotificationResponse createNotification(UUID userId, CreateNotificationRequest request);

    /**
     * Retrieve a notification by its ID.
     * Includes the full delivery attempt history.
     */
    NotificationResponse getNotification(UUID id);

    /**
     * Retrieve a paged, optionally filtered list of notifications for a user.
     */
    PageResponse<NotificationSummary> listNotifications(
            UUID userId,
            NotificationStatus status,
            NotificationType type,
            Pageable pageable);

    /**
     * Cancel a notification that has not yet been delivered.
    */
    NotificationResponse cancelNotification(UUID id);

    /**
     * Retrieve all delivery attempts for a notification, ordered by attempt number.
     */
    List<DeliveryAttemptResponse> getDeliveryAttempts(UUID notificationId);

    /**
     * Replay a failed notification, resetting its status to PENDING and re-queuing it.
     */
    NotificationResponse replayNotification(UUID id);
}
