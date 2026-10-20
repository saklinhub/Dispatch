package com.dispatch.resilience;

import com.dispatch.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Calculates exponential backoff retry schedules based on attempt number and configured properties.
 * Formula: delaySeconds = min(baseInterval * (multiplier ^ (attemptNumber - 1)), maxInterval)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BackoffCalculator {

    private final AppProperties appProperties;

    /**
     * Calculates the delay in seconds for the given attempt number.
     *
     * @param attemptNumber the 1-based attempt index (e.g. 1, 2, 3...)
     * @return delay duration in seconds
     */
    public long calculateDelaySeconds(int attemptNumber) {
        AppProperties.Recovery recovery = appProperties.getRecovery();
        long baseInterval = recovery.getRetryBaseIntervalSeconds();
        int multiplier = recovery.getRetryMultiplier();
        long maxInterval = recovery.getRetryMaxIntervalSeconds();

        int exponent = Math.max(0, attemptNumber - 1);
        long delay = (long) (baseInterval * Math.pow(multiplier, exponent));

        long finalDelay = Math.min(delay, maxInterval);
        log.debug("Calculated retry delay for attempt {}: {} seconds (base={}, multiplier={}, max={})",
                attemptNumber, finalDelay, baseInterval, multiplier, maxInterval);
        return finalDelay;
    }

    /**
     * Calculates the next retry timestamp starting from a given instant.
     *
     * @param from          base timestamp
     * @param attemptNumber the current attempt number
     * @return the instant when the next retry should occur
     */
    public Instant calculateNextRetry(Instant from, int attemptNumber) {
        long delaySeconds = calculateDelaySeconds(attemptNumber);
        return from.plus(Duration.ofSeconds(delaySeconds));
    }

    /**
     * Calculates the next retry timestamp starting from now.
     *
     * @param attemptNumber the current attempt number
     * @return the instant when the next retry should occur
     */
    public Instant calculateNextRetry(int attemptNumber) {
        return calculateNextRetry(Instant.now(), attemptNumber);
    }
}
