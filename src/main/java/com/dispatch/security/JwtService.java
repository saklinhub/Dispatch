package com.dispatch.security;

import com.dispatch.config.AppProperties;
import com.dispatch.entity.User;
import com.dispatch.entity.enums.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Service responsible for JWT token generation, parsing, and validation using JJWT 0.12.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JwtService {

    private final AppProperties appProperties;

    public static final String CLAIM_USER_ID = "userId";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_TOKEN_TYPE = "type";
    public static final String TOKEN_TYPE_ACCESS = "ACCESS";
    public static final String TOKEN_TYPE_REFRESH = "REFRESH";

    /**
     * Generates an access token for a User entity.
     */
    public String generateAccessToken(User user) {
        return buildToken(
                user.getEmail(),
                Map.of(
                        CLAIM_USER_ID, user.getId().toString(),
                        CLAIM_ROLE, user.getRole().name(),
                        CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS
                ),
                appProperties.getJwt().getExpirationMs()
        );
    }

    /**
     * Generates an access token for UserDetails.
     */
    public String generateAccessToken(UserDetails userDetails, UUID userId, UserRole role) {
        return buildToken(
                userDetails.getUsername(),
                Map.of(
                        CLAIM_USER_ID, userId.toString(),
                        CLAIM_ROLE, role.name(),
                        CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS
                ),
                appProperties.getJwt().getExpirationMs()
        );
    }

    /**
     * Generates a refresh token for a User entity.
     */
    public String generateRefreshToken(User user) {
        return buildToken(
                user.getEmail(),
                Map.of(
                        CLAIM_USER_ID, user.getId().toString(),
                        CLAIM_ROLE, user.getRole().name(),
                        CLAIM_TOKEN_TYPE, TOKEN_TYPE_REFRESH
                ),
                appProperties.getJwt().getRefreshExpirationMs()
        );
    }

    /**
     * Generates a refresh token for UserDetails.
     */
    public String generateRefreshToken(UserDetails userDetails, UUID userId, UserRole role) {
        return buildToken(
                userDetails.getUsername(),
                Map.of(
                        CLAIM_USER_ID, userId.toString(),
                        CLAIM_ROLE, role.name(),
                        CLAIM_TOKEN_TYPE, TOKEN_TYPE_REFRESH
                ),
                appProperties.getJwt().getRefreshExpirationMs()
        );
    }

    private String buildToken(String subject, Map<String, Object> extraClaims, long expirationMs) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(subject)
                .claims(extraClaims)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Extracts subject (email) from token.
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extracts user ID from token.
     */
    public UUID extractUserId(String token) {
        String userIdStr = extractClaim(token, claims -> claims.get(CLAIM_USER_ID, String.class));
        return userIdStr != null ? UUID.fromString(userIdStr) : null;
    }

    /**
     * Extracts user role from token.
     */
    public UserRole extractRole(String token) {
        String roleStr = extractClaim(token, claims -> claims.get(CLAIM_ROLE, String.class));
        return roleStr != null ? UserRole.valueOf(roleStr) : null;
    }

    /**
     * Extracts token type (ACCESS or REFRESH) from token.
     */
    public String extractTokenType(String token) {
        return extractClaim(token, claims -> claims.get(CLAIM_TOKEN_TYPE, String.class));
    }

    /**
     * Extracts expiration date from token.
     */
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * Extracts a single claim using a resolver function.
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Parses and returns all claims from a JWT token.
     */
    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Validates whether a token is valid for a given user.
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            final String username = extractUsername(token);
            return (username.equalsIgnoreCase(userDetails.getUsername())) && !isTokenExpired(token);
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Validates whether a token is a valid refresh token.
     */
    public boolean isRefreshTokenValid(String token, UserDetails userDetails) {
        if (!isTokenValid(token, userDetails)) {
            return false;
        }
        return TOKEN_TYPE_REFRESH.equalsIgnoreCase(extractTokenType(token));
    }

    /**
     * Checks if a token has expired.
     */
    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public long getExpirationMs() {
        return appProperties.getJwt().getExpirationMs();
    }

    private SecretKey getSigningKey() {
        String secret = appProperties.getJwt().getSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT signing secret must not be blank");
        }
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
