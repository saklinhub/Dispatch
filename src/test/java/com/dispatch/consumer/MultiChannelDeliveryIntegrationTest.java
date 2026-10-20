package com.dispatch.consumer;

import com.dispatch.delivery.DeliveryDispatcher;
import com.dispatch.delivery.DeliveryResult;
import com.dispatch.dto.message.NotificationMessage;
import com.dispatch.dto.request.CreateNotificationRequest;
import com.dispatch.dto.response.NotificationResponse;
import com.dispatch.entity.DeliveryAttempt;
import com.dispatch.entity.InboxMessage;
import com.dispatch.entity.Notification;
import com.dispatch.entity.User;
import com.dispatch.entity.enums.AttemptStatus;
import com.dispatch.entity.enums.NotificationStatus;
import com.dispatch.entity.enums.NotificationType;
import com.dispatch.entity.enums.UserRole;
import com.dispatch.repository.DeliveryAttemptRepository;
import com.dispatch.repository.InboxMessageRepository;
import com.dispatch.repository.NotificationPreferenceRepository;
import com.dispatch.repository.NotificationRepository;
import com.dispatch.repository.OutboxEventRepository;
import com.dispatch.repository.UserRepository;
import com.dispatch.service.NotificationService;
import com.dispatch.service.outbox.OutboxPoller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class MultiChannelDeliveryIntegrationTest {

    @Autowired
    private DeliveryDispatcher deliveryDispatcher;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private DeliveryAttemptRepository deliveryAttemptRepository;

    @Autowired
    private InboxMessageRepository inboxMessageRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxPoller outboxPoller;

    private User testUser;

    @BeforeEach
    void setUp() {
        deliveryAttemptRepository.deleteAll();
        inboxMessageRepository.deleteAll();
        outboxEventRepository.deleteAll();
        notificationRepository.deleteAll();
        preferenceRepositoryCleanup();
        userRepository.deleteAll();

        testUser = userRepository.save(User.builder()
                .email("delivery.tester@dispatch.io")
                .passwordHash("$2a$10$abcdefghijklmnopqrstuvwxyz123456")
                .fullName("Delivery Tester")
                .phoneNumber("+15555550400")
                .role(UserRole.USER)
                .build());
    }

    @Autowired
    private NotificationPreferenceRepository preferenceRepository;

    private void preferenceRepositoryCleanup() {
        preferenceRepository.deleteAll();
    }

    @Test
    @DisplayName("In-App notification delivery creates inbox message and successful attempt record")
    void testInAppDelivery() {
        CreateNotificationRequest request = CreateNotificationRequest.builder()
                .type(NotificationType.IN_APP)
                .recipientAddress(testUser.getId().toString())
                .subject("Payment Received")
                .body("You received a payment of $50.00.")
                .build();

        NotificationResponse response = notificationService.createNotification(testUser.getId(), request);

        NotificationMessage message = NotificationMessage.builder()
                .notificationId(response.getId())
                .userId(testUser.getId())
                .type(NotificationType.IN_APP)
                .recipientAddress(testUser.getId().toString())
                .subject(request.getSubject())
                .body(request.getBody())
                .build();

        DeliveryResult result = deliveryDispatcher.dispatch(message);
        assertTrue(result.isSuccess());

        Notification updated = notificationRepository.findById(response.getId()).orElseThrow();
        assertEquals(NotificationStatus.DELIVERED, updated.getStatus());

        List<DeliveryAttempt> attempts = deliveryAttemptRepository.findByNotificationIdOrderByAttemptNumberAsc(response.getId());
        assertEquals(1, attempts.size());
        assertEquals(AttemptStatus.SUCCESS, attempts.get(0).getStatus());
        assertEquals(1, attempts.get(0).getAttemptNumber());

        Page<InboxMessage> inboxMessages = inboxMessageRepository.findByUserIdOrderByCreatedAtDesc(testUser.getId(), PageRequest.of(0, 10));
        assertEquals(1, inboxMessages.getTotalElements());
        InboxMessage first = inboxMessages.getContent().get(0);
        assertEquals("Payment Received", first.getTitle());
        assertEquals("You received a payment of $50.00.", first.getBody());
        assertFalse(first.isRead());
    }

    @Test
    @DisplayName("SMS notification delivery logs simulation and records successful delivery attempt")
    void testSmsDelivery() {
        CreateNotificationRequest request = CreateNotificationRequest.builder()
                .type(NotificationType.SMS)
                .recipientAddress("+15555550400")
                .body("Your verification code is 445566.")
                .build();

        NotificationResponse response = notificationService.createNotification(testUser.getId(), request);

        NotificationMessage message = NotificationMessage.builder()
                .notificationId(response.getId())
                .userId(testUser.getId())
                .type(NotificationType.SMS)
                .recipientAddress("+15555550400")
                .body(request.getBody())
                .build();

        DeliveryResult result = deliveryDispatcher.dispatch(message);
        assertTrue(result.isSuccess());

        Notification updated = notificationRepository.findById(response.getId()).orElseThrow();
        assertEquals(NotificationStatus.DELIVERED, updated.getStatus());

        List<DeliveryAttempt> attempts = deliveryAttemptRepository.findByNotificationIdOrderByAttemptNumberAsc(response.getId());
        assertEquals(1, attempts.size());
        assertEquals(AttemptStatus.SUCCESS, attempts.get(0).getStatus());
        assertTrue(attempts.get(0).getProviderResponse().contains("Simulated SMS"));
    }

    @Test
    @DisplayName("Email notification delivery logs simulation and records successful delivery attempt")
    void testEmailDelivery() {
        CreateNotificationRequest request = CreateNotificationRequest.builder()
                .type(NotificationType.EMAIL)
                .recipientAddress("delivery.tester@dispatch.io")
                .subject("Welcome to Dispatch")
                .body("Thank you for joining Dispatch!")
                .build();

        NotificationResponse response = notificationService.createNotification(testUser.getId(), request);

        NotificationMessage message = NotificationMessage.builder()
                .notificationId(response.getId())
                .userId(testUser.getId())
                .type(NotificationType.EMAIL)
                .recipientAddress(request.getRecipientAddress())
                .subject(request.getSubject())
                .body(request.getBody())
                .build();

        DeliveryResult result = deliveryDispatcher.dispatch(message);
        assertTrue(result.isSuccess());

        Notification updated = notificationRepository.findById(response.getId()).orElseThrow();
        assertEquals(NotificationStatus.DELIVERED, updated.getStatus());

        List<DeliveryAttempt> attempts = deliveryAttemptRepository.findByNotificationIdOrderByAttemptNumberAsc(response.getId());
        assertEquals(1, attempts.size());
        assertEquals(AttemptStatus.SUCCESS, attempts.get(0).getStatus());
    }

    @Test
    @DisplayName("Cancelled notifications abort delivery without changing state to DELIVERED")
    void testCancelledNotificationAbortsDelivery() {
        CreateNotificationRequest request = CreateNotificationRequest.builder()
                .type(NotificationType.EMAIL)
                .recipientAddress("cancel.tester@dispatch.io")
                .subject("To Be Cancelled")
                .body("This should not be delivered")
                .build();

        NotificationResponse response = notificationService.createNotification(testUser.getId(), request);
        notificationService.cancelNotification(response.getId());

        NotificationMessage message = NotificationMessage.builder()
                .notificationId(response.getId())
                .userId(testUser.getId())
                .type(NotificationType.EMAIL)
                .recipientAddress("cancel.tester@dispatch.io")
                .subject("To Be Cancelled")
                .body("This should not be delivered")
                .build();

        DeliveryResult result = deliveryDispatcher.dispatch(message);
        assertFalse(result.isSuccess());
        assertEquals("CANCELLED", result.getErrorCode());

        Notification updated = notificationRepository.findById(response.getId()).orElseThrow();
        assertEquals(NotificationStatus.CANCELLED, updated.getStatus());
        assertEquals(0, deliveryAttemptRepository.countByNotificationId(response.getId()));
    }

    @Test
    @DisplayName("Full asynchronous pipeline: Outbox poller -> RabbitMQ -> Consumer -> Delivery")
    void testFullAsynchronousPipeline() throws Exception {
        CreateNotificationRequest request = CreateNotificationRequest.builder()
                .type(NotificationType.IN_APP)
                .recipientAddress(testUser.getId().toString())
                .subject("Async End-to-End")
                .body("Pipeline message routed via RabbitMQ consumer")
                .build();

        NotificationResponse created = notificationService.createNotification(testUser.getId(), request);

        // Run outbox poller to publish to RabbitMQ
        outboxPoller.pollAndPublish();

        // Wait for RabbitMQ consumer to process the message asynchronously
        boolean delivered = false;
        for (int i = 0; i < 20; i++) {
            Notification current = notificationRepository.findById(created.getId()).orElse(null);
            if (current != null && current.getStatus() == NotificationStatus.DELIVERED) {
                delivered = true;
                break;
            }
            TimeUnit.MILLISECONDS.sleep(250);
        }

        assertTrue(delivered, "Notification should have been delivered by RabbitMQ consumer");

        Page<InboxMessage> inbox = inboxMessageRepository.findByUserIdOrderByCreatedAtDesc(testUser.getId(), PageRequest.of(0, 10));
        assertFalse(inbox.isEmpty());
        assertEquals("Async End-to-End", inbox.getContent().get(0).getTitle());
    }
}
