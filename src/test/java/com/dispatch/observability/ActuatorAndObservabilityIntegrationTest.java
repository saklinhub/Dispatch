package com.dispatch.observability;

import com.dispatch.entity.User;
import com.dispatch.entity.enums.UserRole;
import com.dispatch.repository.UserRepository;
import com.dispatch.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ActuatorAndObservabilityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    private User adminUser;
    private User regularUser;
    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUp() {
        adminUser = userRepository.save(User.builder()
                .email("admin.obs." + UUID.randomUUID() + "@dispatch.io")
                .passwordHash("$2a$10$abcdefghijklmnopqrstuvwxyz123456")
                .fullName("Admin User")
                .role(UserRole.ADMIN)
                .build());

        regularUser = userRepository.save(User.builder()
                .email("user.obs." + UUID.randomUUID() + "@dispatch.io")
                .passwordHash("$2a$10$abcdefghijklmnopqrstuvwxyz123456")
                .fullName("Regular User")
                .role(UserRole.USER)
                .build());

        adminToken = jwtService.generateAccessToken(adminUser);
        userToken = jwtService.generateAccessToken(regularUser);
    }

    @Test
    @DisplayName("GET /actuator/health is publicly accessible and returns UP")
    void testPublicHealthEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")));
    }

    @Test
    @DisplayName("GET /actuator/health when authenticated includes custom notificationSystem indicator details")
    void testDetailedHealthIndicator() throws Exception {
        mockMvc.perform(get("/actuator/health")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")))
                .andExpect(jsonPath("$.components.notificationSystem.status", is("UP")))
                .andExpect(jsonPath("$.components.notificationSystem.details.status", is("OPERATIONAL")));
    }

    @Test
    @DisplayName("GET /actuator/info is publicly accessible")
    void testInfoEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /actuator lists available endpoints")
    void testActuatorRoot() throws Exception {
        mockMvc.perform(get("/actuator"))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /actuator/prometheus is accessible and returns Prometheus metrics")
    void testPrometheusEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("jvm_memory_used_bytes")));
    }

    @Test
    @DisplayName("GET /actuator/metrics requires ADMIN role")
    void testMetricsEndpointAccessControl() throws Exception {
        // Unauthenticated -> 401 Unauthorized
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized());

        // Regular user -> 403 Forbidden
        mockMvc.perform(get("/actuator/metrics")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden());

        // Admin user -> 200 OK
        mockMvc.perform(get("/actuator/metrics")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.names").isArray());
    }
}
