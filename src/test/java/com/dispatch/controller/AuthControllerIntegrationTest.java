package com.dispatch.controller;

import com.dispatch.dto.request.LoginRequest;
import com.dispatch.dto.request.RefreshTokenRequest;
import com.dispatch.dto.request.RegisterRequest;
import com.dispatch.repository.DeliveryAttemptRepository;
import com.dispatch.repository.InboxMessageRepository;
import com.dispatch.repository.NotificationPreferenceRepository;
import com.dispatch.repository.NotificationRepository;
import com.dispatch.repository.OutboxEventRepository;
import com.dispatch.repository.UserRepository;
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

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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

    @BeforeEach
    void setUp() {
        deliveryAttemptRepository.deleteAll();
        inboxMessageRepository.deleteAll();
        notificationRepository.deleteAll();
        preferenceRepository.deleteAll();
        outboxEventRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("POST /api/v1/auth/register creates user and returns JWT tokens")
    void testRegisterSuccess() throws Exception {
        RegisterRequest req = RegisterRequest.builder()
                .email("test.register@dispatch.io")
                .password("Password@123")
                .fullName("Register Test")
                .phoneNumber("+15555550200")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.refreshToken", notNullValue()))
                .andExpect(jsonPath("$.tokenType", is("Bearer")))
                .andExpect(jsonPath("$.user.email", is("test.register@dispatch.io")))
                .andExpect(jsonPath("$.user.fullName", is("Register Test")))
                .andExpect(jsonPath("$.user.role", is("USER")));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register fails on duplicate email with 409 Conflict")
    void testRegisterDuplicateEmail() throws Exception {
        RegisterRequest req = RegisterRequest.builder()
                .email("duplicate@dispatch.io")
                .password("Password@123")
                .fullName("First User")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.message", containsString("already exists")));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login authenticates and returns JWT tokens")
    void testLoginSuccess() throws Exception {
        RegisterRequest regReq = RegisterRequest.builder()
                .email("login.test@dispatch.io")
                .password("Secret@123")
                .fullName("Login User")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regReq)))
                .andExpect(status().isCreated());

        LoginRequest loginReq = LoginRequest.builder()
                .email("login.test@dispatch.io")
                .password("Secret@123")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.refreshToken", notNullValue()))
                .andExpect(jsonPath("$.user.email", is("login.test@dispatch.io")));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login fails with 401 on wrong password")
    void testLoginInvalidCredentials() throws Exception {
        RegisterRequest regReq = RegisterRequest.builder()
                .email("wrong.pwd@dispatch.io")
                .password("Correct@123")
                .fullName("User")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regReq)))
                .andExpect(status().isCreated());

        LoginRequest loginReq = LoginRequest.builder()
                .email("wrong.pwd@dispatch.io")
                .password("IncorrectPassword")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }

    @Test
    @DisplayName("POST /api/v1/auth/refresh issues new token pair")
    void testRefreshTokenSuccess() throws Exception {
        RegisterRequest regReq = RegisterRequest.builder()
                .email("refresh.test@dispatch.io")
                .password("Refresh@123")
                .fullName("Refresh User")
                .build();

        MvcResult regResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regReq)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode root = objectMapper.readTree(regResult.getResponse().getContentAsString());
        String refreshToken = root.get("refreshToken").asText();

        RefreshTokenRequest refreshReq = RefreshTokenRequest.builder()
                .refreshToken(refreshToken)
                .build();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.refreshToken", notNullValue()));
    }

    @Test
    @DisplayName("GET /api/v1/auth/me returns current user profile with valid Bearer token")
    void testGetCurrentUserWithToken() throws Exception {
        RegisterRequest regReq = RegisterRequest.builder()
                .email("me.test@dispatch.io")
                .password("Password@123")
                .fullName("Me User")
                .build();

        MvcResult regResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regReq)))
                .andExpect(status().isCreated())
                .andReturn();

        String accessToken = objectMapper.readTree(regResult.getResponse().getContentAsString())
                .get("accessToken").asText();

        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is("me.test@dispatch.io")))
                .andExpect(jsonPath("$.fullName", is("Me User")));
    }

    @Test
    @DisplayName("Unauthenticated request to protected endpoint returns 401 Unauthorized")
    void testUnauthenticatedAccessReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("Unauthorized")));
    }

    @Test
    @DisplayName("Authenticated request to protected endpoint is permitted by security filter")
    void testAuthenticatedAccessPermitted() throws Exception {
        RegisterRequest regReq = RegisterRequest.builder()
                .email("protected.test@dispatch.io")
                .password("Password@123")
                .fullName("Protected User")
                .build();

        MvcResult regResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regReq)))
                .andExpect(status().isCreated())
                .andReturn();

        String accessToken = objectMapper.readTree(regResult.getResponse().getContentAsString())
                .get("accessToken").asText();

        // Notification doesn't exist, so expect 404 Not Found (not 401 Unauthorized)
        mockMvc.perform(get("/api/v1/notifications/" + UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)));
    }
}
