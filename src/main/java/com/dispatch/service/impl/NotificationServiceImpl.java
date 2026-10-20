package com.dispatch.service.impl;

import com.dispatch.dto.request.CreateNotificationRequest;
import com.dispatch.dto.response.DeliveryAttemptResponse;
import com.dispatch.dto.response.NotificationResponse;
import com.dispatch.dto.response.NotificationSummary;
import com.dispatch.dto.response.PageResponse;
import com.dispatch.entity.Notification;
import com.dispatch.entity.OutboxEvent;
import com.dispatch.entity.User;
import com.dispatch.entity.enums.NotificationStatus;
import com.dispatch.entity.enums.NotificationType;
import com.dispatch.entity.enums.OutboxEventStatus;
import com.dispatch.exception.InvalidStateException;
import com.dispatch.exception.ResourceNotFoundException;
import com.dispatch.mapper.NotificationMapper;
import com.dispatch.repository.DeliveryAttemptRepository;
import com.dispatch.repository.NotificationRepository;
import com.dispatch.repository.OutboxEventRepository;
import com.dispatch.repository.UserRepository;
import com.dispatch.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final NotificationMapper mapper;

    //Create

    @Override
    @Transactional
    public NotificationResponse createNotification(UUID userId, CreateNotificationRequest req) {

        // Idempotency check — return existing if key already seen
        if (req.getIdempotencyKey() != null) {
            var existing = notificationRepository.findByIdempotencyKey(req.getIdempotencyKey());
            if (existing.isPresent()) {
                log.info("Idempotency key '{}' already exists — returning existing notification {}",
                        req.getIdempotencyKey(), existing.get().getId());
                return mapper.toResponse(existing.get());
            }
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.user(userId.toString()));

        Notification notification = Notification.builder()
                .user(user)
                .type(req.getType())
                .subject(req.getSubject())
                .body(req.getBody())
                .recipientAddress(req.getRecipientAddress())
                .idempotencyKey(req.getIdempotencyKey())
                .channelMetadata(req.getChannelMetadata())
                .scheduledAt(req.getScheduledAt())
                .status(NotificationStatus.PENDING)
                .build();

        notification = notificationRepository.save(notification);

        // Transactional Outbox: Write outbox event atomically with the notification
        Map<String, Object> payload = new HashMap<>();
        payload.put("notificationId", notification.getId().toString());
        payload.put("userId", user.getId().toString());
        payload.put("type", notification.getType().name());
        payload.put("recipientAddress", notification.getRecipientAddress());
        payload.put("subject", notification.getSubject());
        payload.put("body", notification.getBody());
        if (notification.getIdempotencyKey() != null) {
            payload.put("idempotencyKey", notification.getIdempotencyKey());
        }
        if (notification.getChannelMetadata() != null) {
            payload.put("channelMetadata", notification.getChannelMetadata());
        }
        if (notification.getScheduledAt() != null) {
            payload.put("scheduledAt", notification.getScheduledAt().toString());
        }

        OutboxEvent outboxEvent = OutboxEvent.builder()
                .aggregateType("NOTIFICATION")
                .aggregateId(notification.getId())
                .eventType("NOTIFICATION_CREATED")
                .payload(payload)
                .status(OutboxEventStatus.PENDING)
                .build();

        outboxEventRepository.save(outboxEvent);

        log.info("Created notification {} and queued outbox event {}", notification.getId(), outboxEvent.getId());
        return mapper.toResponse(notification);
    }

    //Read

    @Override
    public NotificationResponse getNotification(UUID id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.notification(id.toString()));
        return mapper.toResponse(notification);
    }

    @Override
    public PageResponse<NotificationSummary> listNotifications(
            UUID userId, NotificationStatus status, NotificationType type, Pageable pageable) {

        Page<Notification> page;

        if (status != null) {
            page = notificationRepository.findByUserIdAndStatus(userId, status, pageable);
        } else {
            page = notificationRepository.findByUserId(userId, pageable);
        }

        Page<NotificationSummary> summaryPage = page.map(mapper::toSummary);
        return mapper.toPageResponse(summaryPage);
    }

    @Override
    public List<DeliveryAttemptResponse> getDeliveryAttempts(UUID notificationId) {
        if (!notificationRepository.existsById(notificationId)) {
            throw ResourceNotFoundException.notification(notificationId.toString());
        }
        return deliveryAttemptRepository
                .findByNotificationIdOrderByAttemptNumberAsc(notificationId)
                .stream()
                .map(mapper::toAttemptResponse)
                .toList();
    }

    //Cancel

    @Override
    @Transactional
    public NotificationResponse cancelNotification(UUID id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.notification(id.toString()));

        if (notification.getStatus() == NotificationStatus.DELIVERED
                || notification.getStatus() == NotificationStatus.CANCELLED) {
            throw new InvalidStateException(
                    "Cannot cancel a notification in status: " + notification.getStatus());
        }

        notification.setStatus(NotificationStatus.CANCELLED);
        notification = notificationRepository.save(notification);

        // Record cancellation event in outbox
        OutboxEvent cancelEvent = OutboxEvent.builder()
                .aggregateType("NOTIFICATION")
                .aggregateId(notification.getId())
                .eventType("NOTIFICATION_CANCELLED")
                .payload(Map.of(
                        "notificationId", notification.getId().toString(),
                        "userId", notification.getUser().getId().toString(),
                        "status", "CANCELLED"
                ))
                .status(OutboxEventStatus.PENDING)
                .build();
        outboxEventRepository.save(cancelEvent);

        log.info("Cancelled notification {} and queued cancel outbox event {}", id, cancelEvent.getId());
        return mapper.toResponse(notification);
    }
}
