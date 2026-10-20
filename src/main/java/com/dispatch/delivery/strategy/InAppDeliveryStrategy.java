package com.dispatch.delivery.strategy;

import com.dispatch.delivery.DeliveryResult;
import com.dispatch.delivery.DeliveryStrategy;
import com.dispatch.dto.message.NotificationMessage;
import com.dispatch.entity.InboxMessage;
import com.dispatch.entity.Notification;
import com.dispatch.entity.User;
import com.dispatch.entity.enums.NotificationType;
import com.dispatch.repository.InboxMessageRepository;
import com.dispatch.repository.NotificationRepository;
import com.dispatch.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Strategy implementation for delivering in-app notifications by persisting them to user inbox.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InAppDeliveryStrategy implements DeliveryStrategy {

    private final InboxMessageRepository inboxMessageRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Override
    public NotificationType supportedType() {
        return NotificationType.IN_APP;
    }

    @Override
    @Transactional
    public DeliveryResult deliver(NotificationMessage message) {
        try {
            Notification notification = notificationRepository.findById(message.getNotificationId()).orElse(null);

            User user = null;
            if (message.getUserId() != null) {
                user = userRepository.findById(message.getUserId()).orElse(null);
            }

            if (user == null && notification != null) {
                user = notification.getUser();
            }

            if (user == null) {
                log.warn("Recipient user not found for IN_APP notification {}", message.getNotificationId());
                return DeliveryResult.failure("USER_NOT_FOUND", "Recipient user not found for in-app delivery");
            }

            InboxMessage inboxMessage = InboxMessage.builder()
                    .user(user)
                    .notification(notification)
                    .title(message.getSubject() != null ? message.getSubject() : "Notification")
                    .body(message.getBody())
                    .read(false)
                    .build();

            inboxMessage = inboxMessageRepository.save(inboxMessage);

            log.info("Persisted IN_APP notification {} to inbox message {}",
                    message.getNotificationId(), inboxMessage.getId());
            return DeliveryResult.success("InboxMessage ID: " + inboxMessage.getId());

        } catch (Exception ex) {
            log.error("Failed to persist IN_APP notification {}: {}", message.getNotificationId(), ex.getMessage(), ex);
            return DeliveryResult.failure("IN_APP_PERSISTENCE_FAILED", ex.getMessage());
        }
    }
}
