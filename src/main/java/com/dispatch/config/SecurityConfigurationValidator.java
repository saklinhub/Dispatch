package com.dispatch.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Validates security-critical configuration properties on application startup.
 * Ensures the application refuses to start if insecure secrets or weak keys are detected.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityConfigurationValidator {

    private final AppProperties appProperties;
    private final Environment environment;

    public static final String INSECURE_DEFAULT_SECRET = "dispatch-super-secret-key-minimum-256-bits-change-in-prod";

    @PostConstruct
    public void validateConfiguration() {
        String secret = appProperties.getJwt().getSecret();

        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT signing secret (dispatch.jwt.secret / JWT_SECRET) must not be blank.");
        }

        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException(
                "JWT signing secret is too short! HMAC-SHA256 requires at least 256 bits (32 bytes). " +
                "Current length: " + secretBytes.length + " bytes."
            );
        }

        boolean isProduction = environment.acceptsProfiles(Profiles.of("prod", "production"));

        if (isProduction && INSECURE_DEFAULT_SECRET.equals(secret.trim())) {
            throw new IllegalStateException(
                "CRITICAL SECURITY CONFIGURATION ERROR: The default development JWT secret cannot be used in production! " +
                "You must supply a strong, randomly generated 256-bit secret via the JWT_SECRET environment variable."
            );
        }

        if (INSECURE_DEFAULT_SECRET.equals(secret.trim())) {
            log.warn("=========================================================================================");
            log.warn("SECURITY WARNING: Using default development JWT secret.");
            log.warn("DO NOT USE THIS DEFAULT IN PRODUCTION! Override via the JWT_SECRET environment variable.");
            log.warn("=========================================================================================");
        } else {
            log.info("JWT configuration validated successfully (secret strength: {} bits).", secretBytes.length * 8);
        }
    }
}
