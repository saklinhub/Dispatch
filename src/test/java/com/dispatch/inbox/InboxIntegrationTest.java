package com.dispatch.inbox;

import com.dispatch.entity.InboxMessage;
import com.dispatch.entity.Notification;
import com.dispatch.entity.User;
import com.dispatch.entity.enums.NotificationStatus;
import com.dispatch.entity.enums.NotificationType;
import com.dispatch.entity.enums.UserRole;
import com.dispatch.repository.DeliveryAttemptRepository;
import com.dispatch.repository.InboxMessageRepository;
import com.dispatch.repository.NotificationRepository;
import com.dispatch.repository.UserRepository;
import com.dispatch.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class InboxIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private InboxMessageRepository inboxMessageRepository;

    @Autowired
    private DeliveryAttemptRepository deliveryAttemptRepository;

    @Autowired
    private JwtService jwtService;

    private User testUser;
    private User otherUser;
    private String jwtToken;
    private Notification testNotification;

    @BeforeEach
    void setUp() {
        deliveryAttemptRepository.deleteAll();
        inboxMessageRepository.deleteAll();
        notificationRepository.deleteAll();
        userRepository.deleteAll();

        testUser = userRepository.save(User.builder()
                .email("inbox.tester." + UUID.randomUUID() + "@dispatch.io")
                .passwordHash("$2a$10$abcdefghijklmnopqrstuvwxyz123456")
                .fullName("Inbox Tester")
                .phoneNumber("+15555550800")
                .role(UserRole.USER)
                .build());

        otherUser = userRepository.save(User.builder()
                .email("other.user." + UUID.randomUUID() + "@dispatch.io")
                .passwordHash("$2a$10$abcdefghijklmnopqrstuvwxyz123456")
                .fullName("Other User")
                .phoneNumber("+15555550801")
                .role(UserRole.USER)
                .build());

        jwtToken = jwtService.generateAccessToken(testUser);

        testNotification = notificationRepository.save(Notification.builder()
                .user(testUser)
                .type(NotificationType.IN_APP)
                .status(NotificationStatus.DELIVERED)
                .subject("Welcome Notification")
                .body("Welcome to Dispatch platform!")
                .recipientAddress(testUser.getEmail())
                .build());
    }

    private InboxMessage createMessage(User user, String title, String body, boolean read, Instant createdAt) {
        return inboxMessageRepository.save(InboxMessage.builder()
                .user(user)
                .notification(testNotification)
                .title(title)
                .body(body)
                .read(read)
                .readAt(read ? Instant.now() : null)
                .createdAt(createdAt != null ? createdAt : Instant.now())
                .build());
    }

    @Test
    @DisplayName("GET /api/v1/users/{userId}/inbox returns paginated messages")
    void testGetInboxPaginated() throws Exception {
        createMessage(testUser, "Msg 1", "Body 1", false, Instant.now().minus(2, ChronoUnit.MINUTES));
        createMessage(testUser, "Msg 2", "Body 2", false, Instant.now().minus(1, ChronoUnit.MINUTES));
        createMessage(testUser, "Msg 3", "Body 3", false, Instant.now());

        mockMvc.perform(get("/api/v1/users/{userId}/inbox", testUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(3)))
                .andExpect(jsonPath("$.totalPages", is(2)))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].title", is("Msg 3")))
                .andExpect(jsonPath("$.content[1].title", is("Msg 2")));
    }

    @Test
    @DisplayName("GET /api/v1/users/{userId}/inbox with read filter")
    void testGetInboxFilterByRead() throws Exception {
        createMessage(testUser, "Unread 1", "Body", false, null);
        createMessage(testUser, "Unread 2", "Body", false, null);
        createMessage(testUser, "Read 1", "Body", true, null);

        // Filter unread
        mockMvc.perform(get("/api/v1/users/{userId}/inbox", testUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                        .param("read", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(2)))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].read", is(false)))
                .andExpect(jsonPath("$.content[1].read", is(false)));

        // Filter read
        mockMvc.perform(get("/api/v1/users/{userId}/inbox", testUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                        .param("read", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].read", is(true)))
                .andExpect(jsonPath("$.content[0].readAt", notNullValue()));
    }

    @Test
    @DisplayName("GET /api/v1/users/{userId}/inbox/unread-count returns correct badge count")
    void testGetUnreadCount() throws Exception {
        createMessage(testUser, "Msg 1", "Body", false, null);
        createMessage(testUser, "Msg 2", "Body", false, null);
        createMessage(testUser, "Msg 3", "Body", true, null);

        mockMvc.perform(get("/api/v1/users/{userId}/inbox/unread-count", testUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId", is(testUser.getId().toString())))
                .andExpect(jsonPath("$.unreadCount", is(2)));
    }

    @Test
    @DisplayName("PATCH /api/v1/users/{userId}/inbox/{messageId}/read marks single message read")
    void testMarkAsRead() throws Exception {
        InboxMessage msg = createMessage(testUser, "Unread Message", "Body", false, null);
        assertFalse(msg.isRead());
        assertNull(msg.getReadAt());

        mockMvc.perform(patch("/api/v1/users/{userId}/inbox/{messageId}/read", testUser.getId(), msg.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(msg.getId().toString())))
                .andExpect(jsonPath("$.read", is(true)))
                .andExpect(jsonPath("$.readAt", notNullValue()));

        InboxMessage updated = inboxMessageRepository.findById(msg.getId()).orElseThrow();
        assertTrue(updated.isRead());
        assertNotNull(updated.getReadAt());
    }

    @Test
    @DisplayName("PATCH /api/v1/users/{userId}/inbox/{messageId}/unread marks message as unread")
    void testMarkAsUnread() throws Exception {
        InboxMessage msg = createMessage(testUser, "Read Message", "Body", true, null);
        assertTrue(msg.isRead());
        assertNotNull(msg.getReadAt());

        mockMvc.perform(patch("/api/v1/users/{userId}/inbox/{messageId}/unread", testUser.getId(), msg.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(msg.getId().toString())))
                .andExpect(jsonPath("$.read", is(false)))
                .andExpect(jsonPath("$.readAt", nullValue()));

        InboxMessage updated = inboxMessageRepository.findById(msg.getId()).orElseThrow();
        assertFalse(updated.isRead());
        assertNull(updated.getReadAt());
    }

    @Test
    @DisplayName("POST /api/v1/users/{userId}/inbox/read-all marks all unread messages as read")
    void testMarkAllAsRead() throws Exception {
        createMessage(testUser, "Msg 1", "Body 1", false, null);
        createMessage(testUser, "Msg 2", "Body 2", false, null);
        createMessage(testUser, "Msg 3", "Body 3", false, null);

        // Also create an unread message for another user to verify isolation
        createMessage(otherUser, "Other Msg", "Body", false, null);

        mockMvc.perform(post("/api/v1/users/{userId}/inbox/read-all", testUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId", is(testUser.getId().toString())))
                .andExpect(jsonPath("$.updatedCount", is(3)));

        assertEquals(0, inboxMessageRepository.countByUserIdAndReadFalse(testUser.getId()));
        // other user's unread count should remain 1
        assertEquals(1, inboxMessageRepository.countByUserIdAndReadFalse(otherUser.getId()));
    }

    @Test
    @DisplayName("DELETE /api/v1/users/{userId}/inbox/{messageId} removes message")
    void testDeleteMessage() throws Exception {
        InboxMessage msg = createMessage(testUser, "To Delete", "Body", false, null);

        mockMvc.perform(delete("/api/v1/users/{userId}/inbox/{messageId}", testUser.getId(), msg.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken))
                .andExpect(status().isNoContent());

        assertFalse(inboxMessageRepository.findById(msg.getId()).isPresent());
    }

    @Test
    @DisplayName("Operating on non-existent or other user's message returns 404")
    void testMessageNotFoundOrBelongingToOtherUser() throws Exception {
        InboxMessage otherMsg = createMessage(otherUser, "Secret", "Body", false, null);

        // Attempting to read another user's message using testUser ID returns 404
        mockMvc.perform(patch("/api/v1/users/{userId}/inbox/{messageId}/read", testUser.getId(), otherMsg.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken))
                .andExpect(status().isNotFound());

        // Random non-existent message id returns 404
        mockMvc.perform(patch("/api/v1/users/{userId}/inbox/{messageId}/read", testUser.getId(), UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken))
                .andExpect(status().isNotFound());

        // Deleting another user's message returns 404
        mockMvc.perform(delete("/api/v1/users/{userId}/inbox/{messageId}", testUser.getId(), otherMsg.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Non-existent user returns 404")
    void testUserNotFound() throws Exception {
        UUID randomUserId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/users/{userId}/inbox", randomUserId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/users/{userId}/inbox/unread-count", randomUserId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken))
                .andExpect(status().isNotFound());
    }
}
