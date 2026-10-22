package com.dispatch.preference;

import com.dispatch.delivery.DeliveryDispatcher;
import com.dispatch.delivery.DeliveryResult;
import com.dispatch.dto.message.NotificationMessage;
import com.dispatch.dto.request.UpdatePreferenceRequest;
import com.dispatch.entity.DeliveryAttempt;
import com.dispatch.entity.Notification;
import com.dispatch.entity.NotificationPreference;
import com.dispatch.entity.User;
import com.dispatch.entity.enums.AttemptStatus;
import com.dispatch.entity.enums.NotificationStatus;
import com.dispatch.entity.enums.NotificationType;
import com.dispatch.entity.enums.UserRole;
import com.dispatch.repository.*;
import com.dispatch.security.JwtService;
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

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PreferenceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationPreferenceRepository preferenceRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private DeliveryAttemptRepository deliveryAttemptRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private InboxMessageRepository inboxMessageRepository;

    @Autowired
    private DeliveryDispatcher deliveryDispatcher;

    @Autowired
    private JwtService jwtService;

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

        String uniqueEmail = "pref.tester." + UUID.randomUUID() + "@dispatch.io";
        testUser = userRepository.save(User.builder()
                .email(uniqueEmail)
                .passwordHash("$2a$10$abcdefghijklmnopqrstuvwxyz123456")
                .fullName("Preference Tester")
                .phoneNumber("+15555550700")
                .role(UserRole.USER)
                .build());

        jwtToken = jwtService.generateAccessToken(testUser);
    }

    @Test
    @DisplayName("GET /api/v1/users/{userId}/preferences creates and returns default preferences")
    void testGetDefaultPreferences() throws Exception {
        mockMvc.perform(get("/api/v1/users/{userId}/preferences", testUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId", is(testUser.getId().toString())))
                .andExpect(jsonPath("$.emailEnabled", is(true)))
                .andExpect(jsonPath("$.smsEnabled", is(true)))
                .andExpect(jsonPath("$.inAppEnabled", is(true)))
                .andExpect(jsonPath("$.timezone", is("UTC")));
    }

    @Test
    @DisplayName("PUT /api/v1/users/{userId}/preferences updates channel opt-ins and quiet hours")
    void testUpdatePreferences() throws Exception {
        UpdatePreferenceRequest req = UpdatePreferenceRequest.builder()
                .smsEnabled(false)
                .preferredEmail("preferred@dispatch.io")
                .preferredPhone("+15555550777")
                .quietHoursStart(LocalTime.of(22, 0))
                .quietHoursEnd(LocalTime.of(7, 30))
                .timezone("America/New_York")
                .build();

        mockMvc.perform(put("/api/v1/users/{userId}/preferences", testUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.smsEnabled", is(false)))
                .andExpect(jsonPath("$.preferredEmail", is("preferred@dispatch.io")))
                .andExpect(jsonPath("$.preferredPhone", is("+15555550777")))
                .andExpect(jsonPath("$.timezone", is("America/New_York")))
                .andExpect(jsonPath("$.quietHoursStart", is("22:00:00")))
                .andExpect(jsonPath("$.quietHoursEnd", is("07:30:00")));
    }

    @Test
    @DisplayName("PATCH /api/v1/users/{userId}/preferences/channels/{channel} toggles channel opt-in")
    void testToggleChannelEndpoint() throws Exception {
        mockMvc.perform(patch("/api/v1/users/{userId}/preferences/channels/EMAIL", testUser.getId())
                        .param("enabled", "false")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailEnabled", is(false)))
                .andExpect(jsonPath("$.smsEnabled", is(true)));
    }

    @Test
    @DisplayName("PUT /api/v1/users/{userId}/preferences with invalid timezone returns 400 Bad Request")
    void testInvalidTimezoneReturns400() throws Exception {
        UpdatePreferenceRequest req = UpdatePreferenceRequest.builder()
                .timezone("Invalid/NonExistent_Zone")
                .build();

        mockMvc.perform(put("/api/v1/users/{userId}/preferences", testUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", containsString("Invalid IANA timezone")));
    }

    @Test
    @DisplayName("Delivery is suppressed and marked CANCELLED when user opts out of non-urgent channel")
    void testChannelOptOutSuppressesDelivery() {
        preferenceRepository.save(NotificationPreference.builder()
                .user(testUser)
                .emailEnabled(true)
                .smsEnabled(false) // Opted out of SMS
                .inAppEnabled(true)
                .timezone("UTC")
                .build());

        Notification notification = notificationRepository.save(Notification.builder()
                .user(testUser)
                .type(NotificationType.SMS)
                .recipientAddress("+15555550700")
                .body("Marketing newsletter SMS")
                .status(NotificationStatus.PENDING)
                .build());

        NotificationMessage message = NotificationMessage.builder()
                .notificationId(notification.getId())
                .userId(testUser.getId())
                .type(NotificationType.SMS)
                .recipientAddress("+15555550700")
                .body(notification.getBody())
                .build();

        DeliveryResult result = deliveryDispatcher.dispatch(message);
        assertFalse(result.isSuccess());
        assertEquals("USER_OPTED_OUT", result.getErrorCode());

        Notification updated = notificationRepository.findById(notification.getId()).orElseThrow();
        assertEquals(NotificationStatus.CANCELLED, updated.getStatus());

        List<DeliveryAttempt> attempts = deliveryAttemptRepository.findByNotificationIdOrderByAttemptNumberAsc(notification.getId());
        assertEquals(1, attempts.size());
        assertEquals(AttemptStatus.FAILURE, attempts.get(0).getStatus());
        assertEquals("USER_OPTED_OUT", attempts.get(0).getErrorCode());
    }

    @Test
    @DisplayName("Urgent notification bypasses channel opt-out and delivers successfully")
    void testUrgentNotificationBypassesChannelOptOut() {
        preferenceRepository.save(NotificationPreference.builder()
                .user(testUser)
                .emailEnabled(true)
                .smsEnabled(false) // Opted out of SMS
                .inAppEnabled(true)
                .timezone("UTC")
                .build());

        Notification notification = notificationRepository.save(Notification.builder()
                .user(testUser)
                .type(NotificationType.SMS)
                .recipientAddress("+15555550700")
                .subject("Security Alert: OTP")
                .body("Your login verification code is 889900")
                .channelMetadata(Map.of("urgent", true))
                .status(NotificationStatus.PENDING)
                .build());

        NotificationMessage message = NotificationMessage.builder()
                .notificationId(notification.getId())
                .userId(testUser.getId())
                .type(NotificationType.SMS)
                .recipientAddress("+15555550700")
                .subject("Security Alert: OTP")
                .body(notification.getBody())
                .channelMetadata(Map.of("urgent", true))
                .build();

        DeliveryResult result = deliveryDispatcher.dispatch(message);
        assertTrue(result.isSuccess(), "Urgent security message should bypass channel opt-out");

        Notification updated = notificationRepository.findById(notification.getId()).orElseThrow();
        assertEquals(NotificationStatus.DELIVERED, updated.getStatus());
    }

    @Test
    @DisplayName("Destination address override redirects notification to user's preferred email")
    void testDestinationAddressOverride() {
        preferenceRepository.save(NotificationPreference.builder()
                .user(testUser)
                .emailEnabled(true)
                .preferredEmail("custom.destination@dispatch.io")
                .timezone("UTC")
                .build());

        Notification notification = notificationRepository.save(Notification.builder()
                .user(testUser)
                .type(NotificationType.EMAIL)
                .recipientAddress("original@dispatch.io")
                .subject("Invoice Attached")
                .body("Please find your monthly invoice.")
                .status(NotificationStatus.PENDING)
                .build());

        NotificationMessage message = NotificationMessage.builder()
                .notificationId(notification.getId())
                .userId(testUser.getId())
                .type(NotificationType.EMAIL)
                .recipientAddress("original@dispatch.io")
                .subject("Invoice Attached")
                .body(notification.getBody())
                .build();

        DeliveryResult result = deliveryDispatcher.dispatch(message);
        assertTrue(result.isSuccess());

        // Recipient address was updated on message to preferred destination
        assertEquals("custom.destination@dispatch.io", message.getRecipientAddress());
    }

    @Test
    @DisplayName("Non-urgent notification during quiet hours is deferred with nextRetryAt set to quietHoursEnd")
    void testQuietHoursDefersDelivery() {
        // Configure quiet hours around current time in UTC
        ZonedDateTime nowUtc = Instant.now().atZone(ZoneId.of("UTC"));
        LocalTime start = nowUtc.toLocalTime().minusHours(1).truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        LocalTime end = nowUtc.toLocalTime().plusHours(2).truncatedTo(java.time.temporal.ChronoUnit.MINUTES);

        preferenceRepository.save(NotificationPreference.builder()
                .user(testUser)
                .emailEnabled(true)
                .quietHoursStart(start)
                .quietHoursEnd(end)
                .timezone("UTC")
                .build());

        Notification notification = notificationRepository.save(Notification.builder()
                .user(testUser)
                .type(NotificationType.EMAIL)
                .recipientAddress(testUser.getEmail())
                .subject("Weekly Newsletter")
                .body("Check out our new features.")
                .status(NotificationStatus.PENDING)
                .build());

        NotificationMessage message = NotificationMessage.builder()
                .notificationId(notification.getId())
                .userId(testUser.getId())
                .type(NotificationType.EMAIL)
                .recipientAddress(testUser.getEmail())
                .subject("Weekly Newsletter")
                .body(notification.getBody())
                .build();

        DeliveryResult result = deliveryDispatcher.dispatch(message);
        assertFalse(result.isSuccess());
        assertEquals("QUIET_HOURS", result.getErrorCode());

        Notification updated = notificationRepository.findById(notification.getId()).orElseThrow();
        assertEquals(NotificationStatus.PROCESSING, updated.getStatus());
        assertNotNull(updated.getScheduledAt(), "scheduledAt must be set to quietHoursEnd");

        List<DeliveryAttempt> attempts = deliveryAttemptRepository.findByNotificationIdOrderByAttemptNumberAsc(notification.getId());
        assertEquals(1, attempts.size());
        assertEquals(AttemptStatus.RETRYING, attempts.get(0).getStatus());
        assertEquals("QUIET_HOURS", attempts.get(0).getErrorCode());
        assertNotNull(attempts.get(0).getNextRetryAt());
    }

    @Test
    @DisplayName("Urgent notification bypasses quiet hours and delivers immediately")
    void testUrgentNotificationBypassesQuietHours() {
        ZonedDateTime nowUtc = Instant.now().atZone(ZoneId.of("UTC"));
        LocalTime start = nowUtc.toLocalTime().minusHours(1).truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        LocalTime end = nowUtc.toLocalTime().plusHours(2).truncatedTo(java.time.temporal.ChronoUnit.MINUTES);

        preferenceRepository.save(NotificationPreference.builder()
                .user(testUser)
                .emailEnabled(true)
                .quietHoursStart(start)
                .quietHoursEnd(end)
                .timezone("UTC")
                .build());

        Notification notification = notificationRepository.save(Notification.builder()
                .user(testUser)
                .type(NotificationType.EMAIL)
                .recipientAddress(testUser.getEmail())
                .subject("Critical Security Alert")
                .body("New login from unknown device.")
                .channelMetadata(Map.of("critical", true))
                .status(NotificationStatus.PENDING)
                .build());

        NotificationMessage message = NotificationMessage.builder()
                .notificationId(notification.getId())
                .userId(testUser.getId())
                .type(NotificationType.EMAIL)
                .recipientAddress(testUser.getEmail())
                .subject("Critical Security Alert")
                .body(notification.getBody())
                .channelMetadata(Map.of("critical", true))
                .build();

        DeliveryResult result = deliveryDispatcher.dispatch(message);
        assertTrue(result.isSuccess(), "Urgent security alert must bypass quiet hours");

        Notification updated = notificationRepository.findById(notification.getId()).orElseThrow();
        assertEquals(NotificationStatus.DELIVERED, updated.getStatus());
    }
}
