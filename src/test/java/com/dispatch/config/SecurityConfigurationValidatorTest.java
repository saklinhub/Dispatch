package com.dispatch.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.*;

class SecurityConfigurationValidatorTest {

    @Test
    @DisplayName("Validation succeeds when strong secret is provided in dev profile")
    void testValidSecretInDevProfile() {
        AppProperties appProperties = new AppProperties();
        appProperties.getJwt().setSecret("a-very-strong-and-secure-custom-key-that-exceeds-256-bits");

        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");

        SecurityConfigurationValidator validator = new SecurityConfigurationValidator(appProperties, env);
        assertDoesNotThrow(validator::validateConfiguration);
    }

    @Test
    @DisplayName("Validation fails when secret is blank")
    void testBlankSecretFails() {
        AppProperties appProperties = new AppProperties();
        appProperties.getJwt().setSecret("   ");

        MockEnvironment env = new MockEnvironment();
        SecurityConfigurationValidator validator = new SecurityConfigurationValidator(appProperties, env);

        IllegalStateException ex = assertThrows(IllegalStateException.class, validator::validateConfiguration);
        assertTrue(ex.getMessage().contains("must not be blank"));
    }

    @Test
    @DisplayName("Validation fails when secret is shorter than 256 bits (32 bytes)")
    void testShortSecretFails() {
        AppProperties appProperties = new AppProperties();
        appProperties.getJwt().setSecret("too-short-key"); // 13 bytes

        MockEnvironment env = new MockEnvironment();
        SecurityConfigurationValidator validator = new SecurityConfigurationValidator(appProperties, env);

        IllegalStateException ex = assertThrows(IllegalStateException.class, validator::validateConfiguration);
        assertTrue(ex.getMessage().contains("too short"));
    }

    @Test
    @DisplayName("Validation fails when default dev secret is used in prod profile")
    void testDefaultSecretFailsInProduction() {
        AppProperties appProperties = new AppProperties();
        appProperties.getJwt().setSecret(SecurityConfigurationValidator.INSECURE_DEFAULT_SECRET);

        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        SecurityConfigurationValidator validator = new SecurityConfigurationValidator(appProperties, env);

        IllegalStateException ex = assertThrows(IllegalStateException.class, validator::validateConfiguration);
        assertTrue(ex.getMessage().contains("CRITICAL SECURITY CONFIGURATION ERROR"));
    }

    @Test
    @DisplayName("Validation succeeds when strong unique secret is used in prod profile")
    void testStrongCustomSecretSucceedsInProduction() {
        AppProperties appProperties = new AppProperties();
        appProperties.getJwt().setSecret("prod-secure-randomly-generated-key-with-high-entropy-12345");

        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        SecurityConfigurationValidator validator = new SecurityConfigurationValidator(appProperties, env);
        assertDoesNotThrow(validator::validateConfiguration);
    }
}
