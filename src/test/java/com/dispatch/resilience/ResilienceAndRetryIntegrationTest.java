package com.dispatch.resilience;

import com.dispatch.config.AppProperties;
import com.dispatch.delivery.DeliveryDispatcher;
import com.dispatch.delivery.DeliveryResult;
import com.dispatch.delivery.DeliveryStrategy;
import com.dispatch.delivery.DeliveryStrategyRegistry;
import com.dispatch.dto.message.NotificationMessage;
import com.dispatch.entity.DeliveryAttempt;
import com.dispatch.entity.Notification;
import com.dispatch.entity.OutboxEvent;
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
import com.dispatch.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ResilienceAndRetryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DeliveryDispatcher deliveryDispatcher;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private DeliveryAttemptRepository deliveryAttemptRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private InboxMessageRepository inboxMessageRepository;

    @Autowired
    private NotificationPreferenceRepository preferenceRepository;

    @Autowired
    private RetryPoller retryPoller;

    @Autowired
    private StuckJobRecoveryService stuckJobRecoveryService;

    @Autowired
    private DlqService dlqService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private AppProperties appProperties;

    private final DeliveryStrategy mockStrategy = org.mockito.Mockito.mock(DeliveryStrategy.class);

    @MockBean
    private DeliveryStrategyRegistry strategyRegistry;

    private User testUser;
    private String jwtToken;

    @BeforeEach
    void setUp() {
        deliveryAttemptRepository.deleteAll();
        inboxMessageRepository.deleteAll();
        outboxEventRepository.deleteAll();
        notificationRepository.deleteAll();
        preferenceRepository.deleteAll();
        userRepository.deleteAll();

        String uniqueEmail = "resilience.tester." + UUID.randomUUID() + "@dispatch.io";
        testUser = userRepository.save(User.builder()
                .email(uniqueEmail)
                .passwordHash("$2a$10$abcdefghijklmnopqrstuvwxyz123456")
                .fullName("Resilience Tester")
                .phoneNumber("+15555550500")
                .role(UserRole.USER)
                .build());

        jwtToken = jwtService.generateAccessToken(testUser);
        when(strategyRegistry.getStrategy(any())).thenReturn(mockStrategy);
    }

    @Test
    @DisplayName("Transient failure sets AttemptStatus.RETRYING and computes nextRetryAt exponential backoff")
    void testTransientErrorSchedulesRetry() {
        when(mockStrategy.deliver(any())).thenReturn(
                DeliveryResult.failure("TWILIO_20429", "Too many requests. Rate limit exceeded.")
        );

        Notification notification = notificationRepository.save(Notification.builder()
                .user(testUser)
                .type(NotificationType.SMS)
                .recipientAddress("+15555550500")
                .body("Retry test code: 1234")
                .status(NotificationStatus.PENDING)
                .build());

        NotificationMessage message = NotificationMessage.builder()
                .notificationId(notification.getId())
                .userId(testUser.getId())
                .type(NotificationType.SMS)
                .recipientAddress("+15555550500")
                .body(notification.getBody())
                .build();

        Instant beforeDispatch = Instant.now();
        DeliveryResult result = deliveryDispatcher.dispatch(message);

        assertFalse(result.isSuccess());
        assertEquals("TWILIO_20429", result.getErrorCode());

        Notification updated = notificationRepository.findById(notification.getId()).orElseThrow();
        assertEquals(NotificationStatus.PROCESSING, updated.getStatus());
        assertNotNull(updated.getScheduledAt(), "scheduledAt should be set to nextRetryAt");

        List<DeliveryAttempt> attempts = deliveryAttemptRepository.findByNotificationIdOrderByAttemptNumberAsc(notification.getId());
        assertEquals(1, attempts.size());
        DeliveryAttempt attempt1 = attempts.get(0);
        assertEquals(AttemptStatus.RETRYING, attempt1.getStatus());
        assertNotNull(attempt1.getNextRetryAt());
        assertTrue(attempt1.getNextRetryAt().isAfter(beforeDispatch));
    }

    @Test
    @DisplayName("Permanent error immediately transitions to FAILED and routes to DLQ without retrying")
    void testPermanentErrorImmediatelyFails() {
        when(mockStrategy.deliver(any())).thenReturn(
                DeliveryResult.failure("TWILIO_21211", "Invalid 'To' Phone Number")
        );

        Notification notification = notificationRepository.save(Notification.builder()
                .user(testUser)
                .type(NotificationType.SMS)
                .recipientAddress("+0000000000")
                .body("Permanent fail test")
                .status(NotificationStatus.PENDING)
                .build());

        NotificationMessage message = NotificationMessage.builder()
                .notificationId(notification.getId())
                .userId(testUser.getId())
                .type(NotificationType.SMS)
                .recipientAddress("+0000000000")
                .body(notification.getBody())
                .build();

        DeliveryResult result = deliveryDispatcher.dispatch(message);

        assertFalse(result.isSuccess());
        assertEquals("TWILIO_21211", result.getErrorCode());

        Notification updated = notificationRepository.findById(notification.getId()).orElseThrow();
        assertEquals(NotificationStatus.FAILED, updated.getStatus());

        List<DeliveryAttempt> attempts = deliveryAttemptRepository.findByNotificationIdOrderByAttemptNumberAsc(notification.getId());
        assertEquals(1, attempts.size());
        DeliveryAttempt attempt = attempts.get(0);
        assertEquals(AttemptStatus.FAILURE, attempt.getStatus());
        assertNull(attempt.getNextRetryAt());
    }

    @Test
    @DisplayName("Transient errors exhaust max attempts and transition to FAILED / DLQ on final attempt")
    void testRetryExhaustionRoutesToDlq() {
        when(mockStrategy.deliver(any())).thenReturn(
                DeliveryResult.failure("TIMEOUT", "Connection timed out")
        );

        Notification notification = notificationRepository.save(Notification.builder()
                .user(testUser)
                .type(NotificationType.EMAIL)
                .recipientAddress("timeout@dispatch.io")
                .subject("Timeout Test")
                .body("Will exhaust retries")
                .status(NotificationStatus.PENDING)
                .build());

        NotificationMessage message = NotificationMessage.builder()
                .notificationId(notification.getId())
                .userId(testUser.getId())
                .type(NotificationType.EMAIL)
                .recipientAddress("timeout@dispatch.io")
                .subject("Timeout Test")
                .body("Will exhaust retries")
                .build();

        int maxAttempts = appProperties.getRabbitmq().getRetryMaxAttempts();

        // Simulate successive attempts up to maxAttempts
        for (int i = 1; i <= maxAttempts; i++) {
            deliveryDispatcher.dispatch(message);
        }

        Notification updated = notificationRepository.findById(notification.getId()).orElseThrow();
        assertEquals(NotificationStatus.FAILED, updated.getStatus(), "Notification should be marked FAILED once retries are exhausted");

        List<DeliveryAttempt> attempts = deliveryAttemptRepository.findByNotificationIdOrderByAttemptNumberAsc(notification.getId());
        assertEquals(maxAttempts, attempts.size());

        // Previous attempts were RETRYING
        for (int i = 0; i < maxAttempts - 1; i++) {
            assertEquals(AttemptStatus.RETRYING, attempts.get(i).getStatus());
        }
        // Final attempt is FAILURE
        assertEquals(AttemptStatus.FAILURE, attempts.get(maxAttempts - 1).getStatus());
        assertNull(attempts.get(maxAttempts - 1).getNextRetryAt());
    }

    @Test
    @DisplayName("RetryPoller picks up due retries and re-enqueues them, clearing nextRetryAt")
    void testRetryPollerPicksUpDueRetries() {
        Notification notification = notificationRepository.save(Notification.builder()
                .user(testUser)
                .type(NotificationType.EMAIL)
                .recipientAddress("poller.retry@dispatch.io")
                .subject("Poller Test")
                .body("Poller will pick this up")
                .status(NotificationStatus.PROCESSING)
                .build());

        // Create a due attempt with nextRetryAt in the past
        DeliveryAttempt dueAttempt = deliveryAttemptRepository.save(DeliveryAttempt.builder()
                .notification(notification)
                .attemptNumber(1)
                .status(AttemptStatus.RETRYING)
                .errorCode("TIMEOUT")
                .errorMessage("Timed out")
                .attemptedAt(Instant.now().minus(Duration.ofMinutes(1)))
                .nextRetryAt(Instant.now().minus(Duration.ofSeconds(10)))
                .build());

        int processed = retryPoller.pollAndRetry();
        assertEquals(1, processed);

        DeliveryAttempt refreshedAttempt = deliveryAttemptRepository.findById(dueAttempt.getId()).orElseThrow();
        assertNull(refreshedAttempt.getNextRetryAt(), "nextRetryAt must be cleared to prevent duplicate polling");
    }

    @Test
    @DisplayName("StuckJobRecoveryService recovers jobs stuck in PROCESSING beyond threshold")
    void testStuckJobRecovery() {
        Notification stuckNotification = notificationRepository.save(Notification.builder()
                .user(testUser)
                .type(NotificationType.SMS)
                .recipientAddress("+15555550500")
                .body("Stuck message")
                .status(NotificationStatus.PROCESSING)
                .build());

        // Force updatedAt to 30 minutes in the past
        Instant thirtyMinutesAgo = Instant.now().minus(Duration.ofMinutes(30));
        notificationRepository.forceUpdateUpdatedAt(stuckNotification.getId(), thirtyMinutesAgo);

        int recovered = stuckJobRecoveryService.recoverStuckJobs();
        assertEquals(1, recovered);

        Notification refreshed = notificationRepository.findById(stuckNotification.getId()).orElseThrow();
        assertEquals(NotificationStatus.PROCESSING, refreshed.getStatus());
        assertTrue(refreshed.getUpdatedAt().isAfter(thirtyMinutesAgo));
    }

    @Test
    @DisplayName("POST /api/v1/notifications/{id}/replay resets FAILED status to PENDING and queues outbox event")
    void testReplayFailedNotificationSuccess() throws Exception {
        Notification failedNotification = notificationRepository.save(Notification.builder()
                .user(testUser)
                .type(NotificationType.EMAIL)
                .recipientAddress("replay@dispatch.io")
                .subject("Replay Subject")
                .body("Replay Body")
                .status(NotificationStatus.FAILED)
                .build());

        mockMvc.perform(post("/api/v1/notifications/{id}/replay", failedNotification.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(failedNotification.getId().toString())))
                .andExpect(jsonPath("$.status", is("PENDING")));

        Notification updated = notificationRepository.findById(failedNotification.getId()).orElseThrow();
        assertEquals(NotificationStatus.PENDING, updated.getStatus());

        List<OutboxEvent> outboxEvents = outboxEventRepository.findByAggregateIdOrderByCreatedAtAsc(failedNotification.getId());
        assertFalse(outboxEvents.isEmpty());
        assertEquals("NOTIFICATION_REPLAYED", outboxEvents.get(0).getEventType());
    }

    @Test
    @DisplayName("POST /api/v1/notifications/{id}/replay on non-FAILED notification returns 422 Unprocessable Entity")
    void testReplayNonFailedNotificationThrowsInvalidState() throws Exception {
        Notification deliveredNotification = notificationRepository.save(Notification.builder()
                .user(testUser)
                .type(NotificationType.EMAIL)
                .recipientAddress("already.delivered@dispatch.io")
                .subject("Already Delivered")
                .body("Cannot be replayed")
                .status(NotificationStatus.DELIVERED)
                .build());

        mockMvc.perform(post("/api/v1/notifications/{id}/replay", deliveredNotification.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status", is(422)))
                .andExpect(jsonPath("$.error", is("Unprocessable Entity")))
                .andExpect(jsonPath("$.message", containsString("Only FAILED notifications can be replayed")));
    }

    @Test
    @DisplayName("DLQ Controller: GET /api/v1/dlq/stats and POST /api/v1/dlq/replay/{id}")
    void testDlqControllerEndpoints() throws Exception {
        Notification failedNotification = notificationRepository.save(Notification.builder()
                .user(testUser)
                .type(NotificationType.EMAIL)
                .recipientAddress("dlq.ctrl@dispatch.io")
                .subject("DLQ Ctrl")
                .body("DLQ Ctrl Body")
                .status(NotificationStatus.FAILED)
                .build());

        // GET /api/v1/dlq/stats
        mockMvc.perform(get("/api/v1/dlq/stats")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queueName", is(appProperties.getRabbitmq().getDlqQueue())))
                .andExpect(jsonPath("$.totalFailedNotifications", greaterThanOrEqualTo(1)));

        // POST /api/v1/dlq/replay/{notificationId}
        mockMvc.perform(post("/api/v1/dlq/replay/{notificationId}", failedNotification.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(failedNotification.getId().toString())))
                .andExpect(jsonPath("$.status", is("PENDING")));
    }
}
