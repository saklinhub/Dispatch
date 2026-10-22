package com.dispatch.audit;

import com.dispatch.entity.DeliveryAttempt;
import com.dispatch.entity.Notification;
import com.dispatch.entity.User;
import com.dispatch.entity.enums.AttemptStatus;
import com.dispatch.entity.enums.NotificationStatus;
import com.dispatch.entity.enums.NotificationType;
import com.dispatch.entity.enums.UserRole;
import com.dispatch.repository.DeliveryAttemptRepository;
import com.dispatch.repository.NotificationRepository;
import com.dispatch.repository.UserRepository;
import com.dispatch.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DeliveryAuditIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private DeliveryAttemptRepository deliveryAttemptRepository;

    @Autowired
    private JwtService jwtService;

    private User testUser;
    private User otherUser;
    private String jwtToken;
    private Notification notificationEmail;
    private Notification notificationSms;
    private Notification otherNotification;

    @BeforeEach
    void setUp() {
        deliveryAttemptRepository.deleteAll();
        notificationRepository.deleteAll();
        userRepository.deleteAll();

        testUser = userRepository.save(User.builder()
                .email("audit.tester." + UUID.randomUUID() + "@dispatch.io")
                .passwordHash("$2a$10$abcdefghijklmnopqrstuvwxyz123456")
                .fullName("Audit Tester")
                .phoneNumber("+15555550900")
                .role(UserRole.USER)
                .build());

        otherUser = userRepository.save(User.builder()
                .email("other.tester." + UUID.randomUUID() + "@dispatch.io")
                .passwordHash("$2a$10$abcdefghijklmnopqrstuvwxyz123456")
                .fullName("Other Tester")
                .phoneNumber("+15555550901")
                .role(UserRole.USER)
                .build());

        jwtToken = jwtService.generateAccessToken(testUser);

        notificationEmail = notificationRepository.save(Notification.builder()
                .user(testUser)
                .type(NotificationType.EMAIL)
                .status(NotificationStatus.DELIVERED)
                .subject("Email Alert")
                .body("Your invoice is ready")
                .recipientAddress(testUser.getEmail())
                .build());

        notificationSms = notificationRepository.save(Notification.builder()
                .user(testUser)
                .type(NotificationType.SMS)
                .status(NotificationStatus.FAILED)
                .subject("SMS Verification")
                .body("Code: 123456")
                .recipientAddress(testUser.getPhoneNumber())
                .build());

        otherNotification = notificationRepository.save(Notification.builder()
                .user(otherUser)
                .type(NotificationType.EMAIL)
                .status(NotificationStatus.DELIVERED)
                .subject("Other Alert")
                .body("Other body")
                .recipientAddress(otherUser.getEmail())
                .build());
    }

    private DeliveryAttempt createAttempt(Notification notification, int attemptNum, AttemptStatus status,
                                          String response, String errCode, String errMsg, Instant attemptedAt) {
        return deliveryAttemptRepository.save(DeliveryAttempt.builder()
                .notification(notification)
                .attemptNumber(attemptNum)
                .status(status)
                .providerResponse(response)
                .errorCode(errCode)
                .errorMessage(errMsg)
                .attemptedAt(attemptedAt != null ? attemptedAt : Instant.now())
                .build());
    }

    @Test
    @DisplayName("GET /api/v1/users/{userId}/delivery-history returns paginated audit history")
    void testGetUserDeliveryHistoryPaginated() throws Exception {
        createAttempt(notificationEmail, 1, AttemptStatus.SUCCESS, "250 OK", null, null,
                Instant.now().minus(5, ChronoUnit.MINUTES));
        createAttempt(notificationSms, 1, AttemptStatus.FAILURE, null, "NETWORK_ERROR", "Gateway timeout",
                Instant.now().minus(2, ChronoUnit.MINUTES));
        // Other user's attempt should not appear in testUser history
        createAttempt(otherNotification, 1, AttemptStatus.SUCCESS, "250 OK", null, null,
                Instant.now().minus(1, ChronoUnit.MINUTES));

        mockMvc.perform(get("/api/v1/users/{userId}/delivery-history", testUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(2)))
                .andExpect(jsonPath("$.content", hasSize(2)))
                // Most recent first
                .andExpect(jsonPath("$.content[0].channelType", is("SMS")))
                .andExpect(jsonPath("$.content[0].status", is("FAILURE")))
                .andExpect(jsonPath("$.content[0].errorCode", is("NETWORK_ERROR")))
                .andExpect(jsonPath("$.content[1].channelType", is("EMAIL")))
                .andExpect(jsonPath("$.content[1].status", is("SUCCESS")))
                .andExpect(jsonPath("$.content[1].providerResponse", is("250 OK")));
    }

    @Test
    @DisplayName("GET /api/v1/users/{userId}/delivery-history with status filter")
    void testGetUserDeliveryHistoryFilteredByStatus() throws Exception {
        createAttempt(notificationEmail, 1, AttemptStatus.SUCCESS, "250 OK", null, null, Instant.now());
        createAttempt(notificationSms, 1, AttemptStatus.FAILURE, null, "PROVIDER_ERROR", "Twilio error", Instant.now());

        // Filter SUCCESS
        mockMvc.perform(get("/api/v1/users/{userId}/delivery-history", testUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                        .param("status", "SUCCESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].status", is("SUCCESS")))
                .andExpect(jsonPath("$.content[0].channelType", is("EMAIL")));

        // Filter FAILURE
        mockMvc.perform(get("/api/v1/users/{userId}/delivery-history", testUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                        .param("status", "FAILURE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].status", is("FAILURE")))
                .andExpect(jsonPath("$.content[0].channelType", is("SMS")));
    }

    @Test
    @DisplayName("GET /api/v1/audit/attempts/{attemptId} returns single attempt inspection")
    void testGetAttemptById() throws Exception {
        DeliveryAttempt attempt = createAttempt(notificationEmail, 1, AttemptStatus.SUCCESS,
                "{\"messageId\":\"msg-12345\"}", null, null, Instant.now());

        mockMvc.perform(get("/api/v1/audit/attempts/{attemptId}", attempt.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(attempt.getId().toString())))
                .andExpect(jsonPath("$.notificationId", is(notificationEmail.getId().toString())))
                .andExpect(jsonPath("$.channelType", is("EMAIL")))
                .andExpect(jsonPath("$.status", is("SUCCESS")))
                .andExpect(jsonPath("$.providerResponse", is("{\"messageId\":\"msg-12345\"}")))
                .andExpect(jsonPath("$.attemptNumber", is(1)));
    }

    @Test
    @DisplayName("Inspection of non-existent attempt returns 404")
    void testAttemptNotFoundReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/audit/attempts/{attemptId}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Query delivery history of non-existent user returns 404")
    void testUserNotFoundReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/users/{userId}/delivery-history", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken))
                .andExpect(status().isNotFound());
    }
}
