package com.dispatch.service.idempotency;

import com.dispatch.dto.request.CreateNotificationRequest;
import com.dispatch.entity.IdempotencyRecord;
import com.dispatch.entity.User;
import com.dispatch.entity.enums.IdempotencyStatus;
import com.dispatch.entity.enums.NotificationType;
import com.dispatch.entity.enums.UserRole;
import com.dispatch.repository.*;
import com.dispatch.security.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class IdempotencyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Autowired
    private DeliveryAttemptRepository deliveryAttemptRepository;

    @Autowired
    private NotificationPreferenceRepository preferenceRepository;

    @Autowired
    private InboxMessageRepository inboxMessageRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private IdempotencyCleanupService cleanupService;

    private User testUser;
    private String jwtToken;

    @BeforeEach
    void setUp() {
        deliveryAttemptRepository.deleteAll();
        inboxMessageRepository.deleteAll();
        outboxEventRepository.deleteAll();
        notificationRepository.deleteAll();
        idempotencyRecordRepository.deleteAll();
        preferenceRepository.deleteAll();
        userRepository.deleteAll();

        String uniqueEmail = "idempotency." + UUID.randomUUID() + "@dispatch.io";
        testUser = userRepository.save(User.builder()
                .email(uniqueEmail)
                .passwordHash("$2a$10$abcdefghijklmnopqrstuvwxyz123456")
                .fullName("Idempotency Tester")
                .phoneNumber("+15555550600")
                .role(UserRole.USER)
                .build());

        jwtToken = jwtService.generateAccessToken(testUser);
    }

    @Test
    @DisplayName("Initial request with Idempotency-Key returns 201 Created and saves COMPLETED record")
    void testInitialRequestSuccess() throws Exception {
        String key = "order-create-" + UUID.randomUUID();
        CreateNotificationRequest req = CreateNotificationRequest.builder()
                .type(NotificationType.EMAIL)
                .recipientAddress("customer@example.com")
                .subject("Order Confirmation")
                .body("Your order #9988 has been confirmed.")
                .channelMetadata(Map.of("orderId", 9988))
                .build();

        mockMvc.perform(post("/api/v1/users/{userId}/notifications", testUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "false"))
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.subject", is("Order Confirmation")));

        assertEquals(1, notificationRepository.count());
        assertEquals(1, outboxEventRepository.count());

        IdempotencyRecord record = idempotencyRecordRepository.findByIdempotencyKey(key).orElseThrow();
        assertEquals(IdempotencyStatus.COMPLETED, record.getStatus());
        assertEquals(201, record.getResponseStatusCode());
        assertNotNull(record.getResponseBody());
        assertNotNull(record.getNotificationId());
    }

    @Test
    @DisplayName("Identical request replay returns 200 OK with cached response and zero duplicates")
    void testIdenticalReplayReturnsCachedResponse() throws Exception {
        String key = "invoice-" + UUID.randomUUID();
        CreateNotificationRequest req = CreateNotificationRequest.builder()
                .type(NotificationType.EMAIL)
                .recipientAddress("billing@example.com")
                .subject("Monthly Invoice")
                .body("Invoice for October 2026.")
                .build();

        // 1. Initial request
        MvcResult firstResult = mockMvc.perform(post("/api/v1/users/{userId}/notifications", testUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "false"))
                .andReturn();

        JsonNode firstJson = objectMapper.readTree(firstResult.getResponse().getContentAsString());
        String notificationId = firstJson.get("id").asText();

        // 2. Exact duplicate replay
        mockMvc.perform(post("/api/v1/users/{userId}/notifications", testUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.id", is(notificationId)))
                .andExpect(jsonPath("$.subject", is("Monthly Invoice")));

        // Zero duplicate entities created
        assertEquals(1, notificationRepository.count(), "Exactly 1 notification entity must exist in database");
        assertEquals(1, outboxEventRepository.count(), "Exactly 1 outbox event must be queued");
    }

    @Test
    @DisplayName("Reusing Idempotency-Key with different payload returns 409 Conflict")
    void testPayloadMismatchReturnsConflict() throws Exception {
        String key = "payout-" + UUID.randomUUID();
        CreateNotificationRequest req1 = CreateNotificationRequest.builder()
                .type(NotificationType.SMS)
                .recipientAddress("+15555550600")
                .body("Payout of $100.00 initiated.")
                .build();

        mockMvc.perform(post("/api/v1/users/{userId}/notifications", testUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated());

        // Reusing same key with DIFFERENT payload ($500 instead of $100)
        CreateNotificationRequest req2 = CreateNotificationRequest.builder()
                .type(NotificationType.SMS)
                .recipientAddress("+15555550600")
                .body("Payout of $500.00 initiated.")
                .build();

        mockMvc.perform(post("/api/v1/users/{userId}/notifications", testUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.error", is("Conflict")))
                .andExpect(jsonPath("$.message", containsString("already used with a different request payload")));
    }

    @Test
    @DisplayName("Concurrent identical requests are safely deduplicated without duplicate DB records")
    void testConcurrentRequestsDeduplication() throws Exception {
        String key = "concurrent-" + UUID.randomUUID();
        CreateNotificationRequest req = CreateNotificationRequest.builder()
                .type(NotificationType.EMAIL)
                .recipientAddress("concurrent@example.com")
                .subject("Concurrent Test")
                .body("Testing thread safety.")
                .build();

        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                latch.await();
                MvcResult res = mockMvc.perform(post("/api/v1/users/{userId}/notifications", testUser.getId())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                                .header("Idempotency-Key", key)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                        .andReturn();
                return res.getResponse().getStatus();
            }));
        }

        latch.countDown(); // Release all threads concurrently
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : futures) {
            statuses.add(f.get());
        }

        // Must create exactly 1 notification entity
        assertEquals(1, notificationRepository.count(), "Only 1 notification should be persisted");

        // Allowed statuses are 201 (initial creator), 200 (cached replay), or 409 (concurrent lock contention)
        for (int status : statuses) {
            assertTrue(status == 201 || status == 200 || status == 409,
                    "Expected status 201, 200, or 409 but got " + status);
        }
    }

    @Test
    @DisplayName("Idempotency inspection API returns record status and request hash")
    void testGetIdempotencyRecord() throws Exception {
        String key = "inspect-" + UUID.randomUUID();
        CreateNotificationRequest req = CreateNotificationRequest.builder()
                .type(NotificationType.EMAIL)
                .recipientAddress("inspect@example.com")
                .subject("Inspect Subject")
                .body("Inspect Body")
                .build();

        mockMvc.perform(post("/api/v1/users/{userId}/notifications", testUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/idempotency/{key}", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idempotencyKey", is(key)))
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.requestHash", notNullValue()))
                .andExpect(jsonPath("$.responseStatusCode", is(201)));
    }

    @Test
    @DisplayName("DELETE /api/v1/idempotency/{key} clears record and allows re-submission")
    void testDeleteIdempotencyRecordAllowsResubmission() throws Exception {
        String key = "delete-" + UUID.randomUUID();
        CreateNotificationRequest req = CreateNotificationRequest.builder()
                .type(NotificationType.EMAIL)
                .recipientAddress("delete@example.com")
                .subject("Delete Subject")
                .body("Delete Body")
                .build();

        mockMvc.perform(post("/api/v1/users/{userId}/notifications", testUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        // Delete the key
        mockMvc.perform(delete("/api/v1/idempotency/{key}", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken))
                .andExpect(status().isNoContent());

        assertFalse(idempotencyRecordRepository.existsByIdempotencyKey(key));

        // Now resubmit with the same key — should be treated as a new request
        mockMvc.perform(post("/api/v1/users/{userId}/notifications", testUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "false"));

        assertEquals(2, notificationRepository.count());
    }

    @Test
    @DisplayName("Cleanup service purges records older than cutoff")
    void testCleanupExpiredRecords() {
        IdempotencyRecord oldRecord = idempotencyRecordRepository.save(IdempotencyRecord.builder()
                .idempotencyKey("old-key-" + UUID.randomUUID())
                .requestHash("some-hash")
                .status(IdempotencyStatus.COMPLETED)
                .lockedUntil(Instant.now())
                .build());

        // Query deletion directly with future cutoff
        int deleted = idempotencyRecordRepository.deleteExpiredRecords(Instant.now().plusSeconds(60));
        assertTrue(deleted >= 1);
        assertFalse(idempotencyRecordRepository.existsById(oldRecord.getId()));
    }
}
