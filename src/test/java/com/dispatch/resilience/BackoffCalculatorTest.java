package com.dispatch.resilience;

import com.dispatch.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class BackoffCalculatorTest {

    private BackoffCalculator calculator;
    private AppProperties appProperties;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        appProperties.getRecovery().setRetryBaseIntervalSeconds(10);
        appProperties.getRecovery().setRetryMultiplier(2);
        appProperties.getRecovery().setRetryMaxIntervalSeconds(3600);

        calculator = new BackoffCalculator(appProperties);
    }

    @Test
    @DisplayName("Exponential backoff scales geometrically based on attempt number")
    void testExponentialDelayCalculation() {
        // attempt 1: 10 * 2^0 = 10s
        assertEquals(10, calculator.calculateDelaySeconds(1));

        // attempt 2: 10 * 2^1 = 20s
        assertEquals(20, calculator.calculateDelaySeconds(2));

        // attempt 3: 10 * 2^2 = 40s
        assertEquals(40, calculator.calculateDelaySeconds(3));

        // attempt 4: 10 * 2^3 = 80s
        assertEquals(80, calculator.calculateDelaySeconds(4));
    }

    @Test
    @DisplayName("Delay is capped at configured retryMaxIntervalSeconds")
    void testDelayCappedAtMaxInterval() {
        // attempt 15: 10 * 2^14 = 163,840s > 3600s max
        assertEquals(3600, calculator.calculateDelaySeconds(15));
    }

    @Test
    @DisplayName("calculateNextRetry adds computed delay to base timestamp")
    void testCalculateNextRetryFromInstant() {
        Instant now = Instant.parse("2026-10-20T12:00:00Z");
        Instant nextRetry = calculator.calculateNextRetry(now, 2); // 20s delay

        assertEquals(Instant.parse("2026-10-20T12:00:20Z"), nextRetry);
    }
}
