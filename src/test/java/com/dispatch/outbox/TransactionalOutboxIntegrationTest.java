package com.dispatch.outbox;

import com.dispatch.dto.request.CreateNotificationRequest;
import com.dispatch.dto.response.NotificationResponse;
import com.dispatch.entity.OutboxEvent;
import com.dispatch.entity.User;
import com.dispatch.entity.enums.NotificationType;
import com.dispatch.entity.enums.OutboxEventStatus;
import com.dispatch.entity.enums.UserRole;
import com.dispatch.repository.DeliveryAttemptRepository;
import com.dispatch.repository.InboxMessageRepository;
import com.dispatch.repository.NotificationPreferenceRepository;
import com.dispatch.repository.NotificationRepository;
import com.dispatch.repository.OutboxEventRepository;
import com.dispatch.repository.UserRepository;
import com.dispatch.service.NotificationService;
import com.dispatch.service.outbox.OutboxPoller;
import com.dispatch.service.outbox.OutboxPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class TransactionalOutboxIntegrationTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DeliveryAttemptRepository deliveryAttemptRepository;

    @Autowired
    private NotificationPreferenceRepository preferenceRepository;

    @Autowired
    private InboxMessageRepository inboxMessageRepository;

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Autowired
    private OutboxPoller outboxPoller;

    private User testUser;

    @BeforeEach
    void setUp() {
        deliveryAttemptRepository.deleteAll();
        inboxMessageRepository.deleteAll();
        outboxEventRepository.deleteAll();
        notificationRepository.deleteAll();
        preferenceRepository.deleteAll();
        userRepository.deleteAll();

        testUser = userRepository.save(User.builder()
                .email("outbox.tester@dispatch.io")
                .passwordHash("$2a$10$abcdefghijklmnopqrstuvwxyz123456")
                .fullName("Outbox Tester")
                .phoneNumber("+15555550300")
                .role(UserRole.USER)
                .build());
    }

    @Test
    @DisplayName("Creating notification atomically persists OutboxEvent with PENDING status")
    void testCreateNotificationSavesOutboxEvent() {
        CreateNotificationRequest request = CreateNotificationRequest.builder()
                .type(NotificationType.EMAIL)
                .recipientAddress("outbox.tester@dispatch.io")
                .subject("Order Shipped")
                .body("Your order #12345 has been dispatched.")
                .channelMetadata(Map.of("trackingNumber", "TRACK-999"))
                .build();

        NotificationResponse response = notificationService.createNotification(testUser.getId(), request);
        assertNotNull(response);
        assertNotNull(response.getId());

        List<OutboxEvent> outboxEvents = outboxEventRepository.findByAggregateIdOrderByCreatedAtAsc(response.getId());
        assertEquals(1, outboxEvents.size());

        OutboxEvent event = outboxEvents.get(0);
        assertEquals("NOTIFICATION", event.getAggregateType());
        assertEquals("NOTIFICATION_CREATED", event.getEventType());
        assertEquals(OutboxEventStatus.PENDING, event.getStatus());
        assertNull(event.getPublishedAt());
        assertNotNull(event.getPayload());
        assertEquals(response.getId().toString(), event.getPayload().get("notificationId"));
        assertEquals("EMAIL", event.getPayload().get("type"));
        assertEquals("Order Shipped", event.getPayload().get("subject"));
    }

    @Test
    @DisplayName("Cancelling notification writes NOTIFICATION_CANCELLED OutboxEvent")
    void testCancelNotificationSavesCancelOutboxEvent() {
        CreateNotificationRequest request = CreateNotificationRequest.builder()
                .type(NotificationType.SMS)
                .recipientAddress("+15555550300")
                .body("Security Code: 987654")
                .build();

        NotificationResponse response = notificationService.createNotification(testUser.getId(), request);
        assertNotNull(response);

        notificationService.cancelNotification(response.getId());

        List<OutboxEvent> outboxEvents = outboxEventRepository.findByAggregateIdOrderByCreatedAtAsc(response.getId());
        assertEquals(2, outboxEvents.size());
        assertEquals("NOTIFICATION_CREATED", outboxEvents.get(0).getEventType());
        assertEquals("NOTIFICATION_CANCELLED", outboxEvents.get(1).getEventType());
    }

    @Test
    @DisplayName("OutboxPublisher publishes event to RabbitMQ and updates status to PUBLISHED")
    void testOutboxPublisherPublishesEvent() {
        CreateNotificationRequest request = CreateNotificationRequest.builder()
                .type(NotificationType.EMAIL)
                .recipientAddress("publisher.test@dispatch.io")
                .subject("Test Notification")
                .body("Testing OutboxPublisher")
                .build();

        NotificationResponse response = notificationService.createNotification(testUser.getId(), request);
        OutboxEvent event = outboxEventRepository.findByAggregateIdOrderByCreatedAtAsc(response.getId()).get(0);
        assertEquals(OutboxEventStatus.PENDING, event.getStatus());

        boolean published = outboxPublisher.publish(event);
        assertTrue(published);

        OutboxEvent reloaded = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertEquals(OutboxEventStatus.PUBLISHED, reloaded.getStatus());
        assertNotNull(reloaded.getPublishedAt());
        assertNull(reloaded.getErrorMessage());
    }

    @Test
    @DisplayName("OutboxPoller pollAndPublish processes pending events")
    void testOutboxPollerPollsAndPublishes() {
        CreateNotificationRequest request = CreateNotificationRequest.builder()
                .type(NotificationType.IN_APP)
                .recipientAddress(testUser.getId().toString())
                .subject("System Notice")
                .body("Maintenance at midnight")
                .build();

        notificationService.createNotification(testUser.getId(), request);
        assertEquals(1, outboxEventRepository.countByStatus(OutboxEventStatus.PENDING));

        outboxPoller.pollAndPublish();

        assertEquals(0, outboxEventRepository.countByStatus(OutboxEventStatus.PENDING));
        assertEquals(1, outboxEventRepository.countByStatus(OutboxEventStatus.PUBLISHED));
    }
}
