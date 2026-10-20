package com.dispatch.resilience;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class ErrorClassifierTest {

    private ErrorClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new ErrorClassifier();
    }

    @ParameterizedTest
    @ValueSource(strings = {"TWILIO_21211", "TWILIO_21614", "TWILIO_21610", "TWILIO_20003", "SMTP_550", "SMTP_554", "USER_NOT_FOUND", "PAYLOAD_INVALID"})
    @DisplayName("Permanent error codes are classified as PERMANENT")
    void testPermanentCodes(String code) {
        assertEquals(ErrorType.PERMANENT, classifier.classify(code, null));
        assertTrue(classifier.isPermanent(code, null));
        assertFalse(classifier.isTransient(code, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"TWILIO_20429", "TWILIO_500", "TWILIO_503", "SMTP_421", "SMTP_450", "TIMEOUT", "NETWORK_ERROR", "RATE_LIMIT"})
    @DisplayName("Transient error codes are classified as TRANSIENT")
    void testTransientCodes(String code) {
        assertEquals(ErrorType.TRANSIENT, classifier.classify(code, null));
        assertTrue(classifier.isTransient(code, null));
        assertFalse(classifier.isPermanent(code, null));
    }

    @Test
    @DisplayName("SMTP 5xx prefix classifies as PERMANENT and 4xx as TRANSIENT")
    void testSmtpPrefixes() {
        assertEquals(ErrorType.PERMANENT, classifier.classify("SMTP_580", null));
        assertEquals(ErrorType.TRANSIENT, classifier.classify("SMTP_499", null));
    }

    @Test
    @DisplayName("Error messages are used as fallback when code is unknown or null")
    void testMessageFallback() {
        assertEquals(ErrorType.PERMANENT, classifier.classify(null, "The recipient was not found"));
        assertEquals(ErrorType.PERMANENT, classifier.classify(null, "Invalid number provided"));
        assertEquals(ErrorType.TRANSIENT, classifier.classify(null, "Connection timed out during handshake"));
        assertEquals(ErrorType.TRANSIENT, classifier.classify(null, "Server busy or rate limit reached"));
    }

    @Test
    @DisplayName("Unrecognized errors default to TRANSIENT to safeguard delivery")
    void testDefaultToTransient() {
        assertEquals(ErrorType.TRANSIENT, classifier.classify("UNKNOWN_CODE", "Something unexpected"));
    }
}
