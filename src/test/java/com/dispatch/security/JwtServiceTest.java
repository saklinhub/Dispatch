package com.dispatch.security;

import com.dispatch.config.AppProperties;
import com.dispatch.entity.User;
import com.dispatch.entity.enums.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;
    private AppProperties appProperties;
    private User testUser;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        appProperties.getJwt().setSecret("dispatch-super-secret-key-minimum-256-bits-change-in-prod");
        appProperties.getJwt().setExpirationMs(3600000L); // 1 hour
        appProperties.getJwt().setRefreshExpirationMs(86400000L); // 24 hours

        jwtService = new JwtService(appProperties);

        testUser = User.builder()
                .id(UUID.randomUUID())
                .email("alice@dispatch.io")
                .passwordHash("hashed-pw")
                .fullName("Alice Smith")
                .role(UserRole.USER)
                .build();
    }

    @Test
    @DisplayName("Generate access token and extract claims correctly")
    void testGenerateAccessTokenAndExtractClaims() {
        String token = jwtService.generateAccessToken(testUser);
        assertNotNull(token);

        String username = jwtService.extractUsername(token);
        assertEquals("alice@dispatch.io", username);

        UUID userId = jwtService.extractUserId(token);
        assertEquals(testUser.getId(), userId);

        UserRole role = jwtService.extractRole(token);
        assertEquals(UserRole.USER, role);

        String tokenType = jwtService.extractTokenType(token);
        assertEquals(JwtService.TOKEN_TYPE_ACCESS, tokenType);

        assertFalse(jwtService.isTokenExpired(token));

        CustomUserDetails userDetails = new CustomUserDetails(testUser);
        assertTrue(jwtService.isTokenValid(token, userDetails));
    }

    @Test
    @DisplayName("Generate refresh token and validate token type")
    void testGenerateRefreshToken() {
        String refreshToken = jwtService.generateRefreshToken(testUser);
        assertNotNull(refreshToken);

        String tokenType = jwtService.extractTokenType(refreshToken);
        assertEquals(JwtService.TOKEN_TYPE_REFRESH, tokenType);

        CustomUserDetails userDetails = new CustomUserDetails(testUser);
        assertTrue(jwtService.isRefreshTokenValid(refreshToken, userDetails));
    }

    @Test
    @DisplayName("Token validation fails for mismatched user")
    void testTokenValidationFailsForDifferentUser() {
        String token = jwtService.generateAccessToken(testUser);

        User anotherUser = User.builder()
                .id(UUID.randomUUID())
                .email("bob@dispatch.io")
                .passwordHash("hashed-pw")
                .fullName("Bob")
                .role(UserRole.USER)
                .build();

        CustomUserDetails bobDetails = new CustomUserDetails(anotherUser);
        assertFalse(jwtService.isTokenValid(token, bobDetails));
    }

    @Test
    @DisplayName("Token validation fails for expired token")
    void testTokenValidationFailsWhenExpired() {
        appProperties.getJwt().setExpirationMs(-1000L); // expired 1s ago
        JwtService expiredJwtService = new JwtService(appProperties);

        String token = expiredJwtService.generateAccessToken(testUser);
        CustomUserDetails userDetails = new CustomUserDetails(testUser);

        assertFalse(expiredJwtService.isTokenValid(token, userDetails));
    }
}
