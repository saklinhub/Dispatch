package com.dispatch.repository;

import com.dispatch.entity.*;
import com.dispatch.entity.enums.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class RepositoryIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private DeliveryAttemptRepository deliveryAttemptRepository;

    @Autowired
    private NotificationPreferenceRepository preferenceRepository;

    @Autowired
    private InboxMessageRepository inboxMessageRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        deliveryAttemptRepository.deleteAll();
        inboxMessageRepository.deleteAll();
        notificationRepository.deleteAll();
        preferenceRepository.deleteAll();
        userRepository.deleteAll();
        outboxEventRepository.deleteAll();

        testUser = userRepository.save(User.builder()
                .email("test.user@dispatch.io")
                .passwordHash("$2a$10$abcdefghijklmnopqrstuvwxyz123456")
                .fullName("Test User")
                .phoneNumber("+15555550100")
                .role(UserRole.USER)
                .build());
    }

    @Test
    @DisplayName("User persistence and querying")
    void testUserPersistence() {
        assertNotNull(testUser.getId());
        assertNotNull(testUser.getCreatedAt());
        assertNotNull(testUser.getUpdatedAt());

        Optional<User> foundByEmail = userRepository.findByEmail("test.user@dispatch.io");
        assertTrue(foundByEmail.isPresent());
        assertEquals("Test User", foundByEmail.get().getFullName());

        assertTrue(userRepository.existsByEmail("test.user@dispatch.io"));
        assertFalse(userRepository.existsByEmail("unknown@dispatch.io"));

        Optional<User> foundByPhone = userRepository.findByPhoneNumber("+15555550100");
        assertTrue(foundByPhone.isPresent());
    }

    @Test
    @DisplayName("Notification persistence with JSONB metadata and idempotency lookup")
    void testNotificationPersistence() {
        String idempotencyKey = "key-" + UUID.randomUUID();

        Notification notification = notificationRepository.save(Notification.builder()
                .user(testUser)
                .type(NotificationType.EMAIL)
                .status(NotificationStatus.PENDING)
                .subject("Welcome to Dispatch")
                .body("Hello, your notification account is ready!")
                .recipientAddress("test.user@dispatch.io")
                .idempotencyKey(idempotencyKey)
                .channelMetadata(Map.of("templateId", "welcome-tpl", "priority", "high"))
                .build());

        assertNotNull(notification.getId());
        assertNotNull(notification.getCreatedAt());
        assertEquals(NotificationStatus.PENDING, notification.getStatus());

        Optional<Notification> found = notificationRepository.findByIdempotencyKey(idempotencyKey);
        assertTrue(found.isPresent());
        assertEquals("Welcome to Dispatch", found.get().getSubject());
        assertEquals("welcome-tpl", found.get().getChannelMetadata().get("templateId"));

        long countPending = notificationRepository.countByStatus(NotificationStatus.PENDING);
        assertTrue(countPending >= 1);

        long userPendingCount = notificationRepository.countByUserIdAndStatus(testUser.getId(), NotificationStatus.PENDING);
        assertEquals(1, userPendingCount);
    }

    @Test
    @DisplayName("Delivery attempt persistence and tracking")
    void testDeliveryAttemptPersistence() {
        Notification notification = notificationRepository.save(Notification.builder()
                .user(testUser)
                .type(NotificationType.SMS)
                .status(NotificationStatus.PROCESSING)
                .body("Security Code: 123456")
                .recipientAddress("+15555550100")
                .build());

        DeliveryAttempt attempt1 = deliveryAttemptRepository.save(DeliveryAttempt.builder()
                .notification(notification)
                .attemptNumber(1)
                .status(AttemptStatus.RETRYING)
                .errorMessage("Network timeout")
                .attemptedAt(Instant.now())
                .nextRetryAt(Instant.now().plusSeconds(60))
                .build());

        DeliveryAttempt attempt2 = deliveryAttemptRepository.save(DeliveryAttempt.builder()
                .notification(notification)
                .attemptNumber(2)
                .status(AttemptStatus.SUCCESS)
                .providerResponse("SM123456789")
                .attemptedAt(Instant.now())
                .build());

        List<DeliveryAttempt> attempts = deliveryAttemptRepository.findByNotificationIdOrderByAttemptNumberAsc(notification.getId());
        assertEquals(2, attempts.size());
        assertEquals(1, attempts.get(0).getAttemptNumber());
        assertEquals(2, attempts.get(1).getAttemptNumber());

        Optional<DeliveryAttempt> latest = deliveryAttemptRepository.findTopByNotificationIdOrderByAttemptNumberDesc(notification.getId());
        assertTrue(latest.isPresent());
        assertEquals(2, latest.get().getAttemptNumber());
        assertEquals(AttemptStatus.SUCCESS, latest.get().getStatus());
    }

    @Test
    @DisplayName("Notification preference persistence and quiet hours")
    void testPreferencePersistence() {
        NotificationPreference preference = preferenceRepository.save(NotificationPreference.builder()
                .user(testUser)
                .emailEnabled(true)
                .smsEnabled(false)
                .inAppEnabled(true)
                .preferredEmail("custom@dispatch.io")
                .preferredPhone("+15555559999")
                .quietHoursStart(LocalTime.of(22, 0))
                .quietHoursEnd(LocalTime.of(8, 0))
                .timezone("America/New_York")
                .build());

        assertNotNull(preference.getId());
        Optional<NotificationPreference> found = preferenceRepository.findByUserId(testUser.getId());
        assertTrue(found.isPresent());
        assertTrue(found.get().isEmailEnabled());
        assertFalse(found.get().isSmsEnabled());
        assertEquals("America/New_York", found.get().getTimezone());
        assertEquals("custom@dispatch.io", found.get().getPreferredEmail());
    }

    @Test
    @DisplayName("Inbox message persistence and read marking")
    void testInboxMessagePersistence() {
        Notification notification = notificationRepository.save(Notification.builder()
                .user(testUser)
                .type(NotificationType.IN_APP)
                .status(NotificationStatus.DELIVERED)
                .body("You received a new update")
                .recipientAddress(testUser.getId().toString())
                .build());

        InboxMessage message = inboxMessageRepository.save(InboxMessage.builder()
                .user(testUser)
                .notification(notification)
                .title("New Update")
                .body("You received a new update")
                .read(false)
                .build());

        assertNotNull(message.getId());
        assertFalse(message.isRead());

        assertEquals(1, inboxMessageRepository.countByUserIdAndReadFalse(testUser.getId()));

        int updated = inboxMessageRepository.markAllAsReadForUser(testUser.getId(), Instant.now());
        assertEquals(1, updated);

        assertEquals(0, inboxMessageRepository.countByUserIdAndReadFalse(testUser.getId()));
    }

    @Test
    @DisplayName("Outbox event persistence with JSONB payload")
    void testOutboxEventPersistence() {
        UUID aggregateId = UUID.randomUUID();

        OutboxEvent event = outboxEventRepository.save(OutboxEvent.builder()
                .aggregateType("NOTIFICATION")
                .aggregateId(aggregateId)
                .eventType("NOTIFICATION_CREATED")
                .payload(Map.of(
                        "notificationId", aggregateId.toString(),
                        "channel", "EMAIL",
                        "recipient", "user@example.com"
                ))
                .status(OutboxEventStatus.PENDING)
                .build());

        assertNotNull(event.getId());
        assertNotNull(event.getCreatedAt());
        assertEquals(OutboxEventStatus.PENDING, event.getStatus());

        List<OutboxEvent> pending = outboxEventRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING);
        assertFalse(pending.isEmpty());
        assertEquals("NOTIFICATION_CREATED", pending.get(0).getEventType());
        assertEquals("EMAIL", pending.get(0).getPayload().get("channel"));

        List<OutboxEvent> paged = outboxEventRepository.findByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING, PageRequest.of(0, 10));
        assertFalse(paged.isEmpty());
    }
}
