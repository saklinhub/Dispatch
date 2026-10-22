package com.dispatch.e2e;

import com.dispatch.dto.request.CreateNotificationRequest;
import com.dispatch.dto.request.RegisterRequest;
import com.dispatch.entity.Notification;
import com.dispatch.entity.enums.AttemptStatus;
import com.dispatch.entity.enums.NotificationStatus;
import com.dispatch.entity.enums.NotificationType;
import com.dispatch.repository.DeliveryAttemptRepository;
import com.dispatch.repository.InboxMessageRepository;
import com.dispatch.repository.NotificationRepository;
import com.dispatch.repository.OutboxEventRepository;
import com.dispatch.service.outbox.OutboxPoller;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end test verifying the complete asynchronous notification lifecycle
 * running against real PostgreSQL and RabbitMQ Testcontainers instances.
 */
class EndToEndNotificationPipelineTest extends BaseTestcontainersTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private InboxMessageRepository inboxMessageRepository;

    @Autowired
    private DeliveryAttemptRepository deliveryAttemptRepository;

    @Autowired
    private OutboxPoller outboxPoller;

    @Test
    @DisplayName("Verify Testcontainers infrastructure health")
    void testContainersAreRunning() {
        assertTrue(postgres.isRunning(), "PostgreSQL container should be running");
        assertTrue(rabbitmq.isRunning(), "RabbitMQ container should be running");
    }

    @Test
    @DisplayName("Complete end-to-end flow: Registration -> Outbox -> RabbitMQ -> Consumer -> InApp Inbox -> Delivery Audit")
    void testEndToEndPipelineFlow() throws Exception {
        // Step 1: Register a new user
        String email = "e2e.user." + UUID.randomUUID() + "@dispatch.io";
        RegisterRequest registerReq = RegisterRequest.builder()
                .email(email)
                .password("Password123!")
                .fullName("E2E Pipeline User")
                .phoneNumber("+15555550999")
                .build();

        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.user.id", notNullValue()))
                .andReturn();

        JsonNode authNode = objectMapper.readTree(registerResult.getResponse().getContentAsString());
        String token = authNode.get("accessToken").asText();
        UUID userId = UUID.fromString(authNode.get("user").get("id").asText());

        // Step 2: Submit an IN_APP notification with Idempotency-Key
        String idempotencyKey = "e2e-key-" + UUID.randomUUID();
        CreateNotificationRequest createReq = CreateNotificationRequest.builder()
                .type(NotificationType.IN_APP)
                .subject("E2E Account Verified")
                .body("Your account was successfully onboarded.")
                .recipientAddress(email)
                .build();

        MvcResult notificationResult = mockMvc.perform(post("/api/v1/users/{userId}/notifications", userId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andExpect(jsonPath("$.type", is("IN_APP")))
                .andReturn();

        JsonNode notifNode = objectMapper.readTree(notificationResult.getResponse().getContentAsString());
        UUID notificationId = UUID.fromString(notifNode.get("id").asText());

        // Verify outbox event was created atomically
        assertEquals(1, outboxEventRepository.findByAggregateIdOrderByCreatedAtAsc(notificationId).size());

        // Step 3: Trigger Outbox Poller to publish outbox event to RabbitMQ
        outboxPoller.pollAndPublish();

        // Step 4: Await asynchronous consumption and delivery (RabbitMQ -> Consumer -> InAppDeliveryStrategy)
        boolean delivered = false;
        for (int i = 0; i < 30; i++) {
            Notification n = notificationRepository.findById(notificationId).orElse(null);
            if (n != null && n.getStatus() == NotificationStatus.DELIVERED) {
                delivered = true;
                break;
            }
            TimeUnit.MILLISECONDS.sleep(200);
        }
        assertTrue(delivered, "Notification should have transitioned to DELIVERED by RabbitMQ consumer");

        // Step 5: Check In-App Inbox API (Unread badge count and message contents)
        mockMvc.perform(get("/api/v1/users/{userId}/inbox/unread-count", userId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount", is(1)));

        MvcResult inboxResult = mockMvc.perform(get("/api/v1/users/{userId}/inbox", userId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].title", is("E2E Account Verified")))
                .andExpect(jsonPath("$.content[0].body", is("Your account was successfully onboarded.")))
                .andExpect(jsonPath("$.content[0].read", is(false)))
                .andReturn();

        JsonNode inboxNode = objectMapper.readTree(inboxResult.getResponse().getContentAsString());
        UUID messageId = UUID.fromString(inboxNode.get("content").get(0).get("id").asText());

        // Step 6: Mark inbox message as read
        mockMvc.perform(patch("/api/v1/users/{userId}/inbox/{messageId}/read", userId, messageId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read", is(true)))
                .andExpect(jsonPath("$.readAt", notNullValue()));

        // Step 7: Check delivery audit history
        mockMvc.perform(get("/api/v1/users/{userId}/delivery-history", userId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].channelType", is("IN_APP")))
                .andExpect(jsonPath("$.content[0].status", is("SUCCESS")));

        // Step 8: Verify Actuator Health
        mockMvc.perform(get("/actuator/health")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")));
    }
}
