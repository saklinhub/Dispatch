package com.dispatch.service.idempotency;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Background worker that periodically cleans up expired idempotency records.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyCleanupService {

    private final IdempotencyService idempotencyService;

    @Scheduled(fixedDelayString = "${dispatch.idempotency.cleanup-interval-ms:3600000}")
    public int runCleanup() {
        log.debug("Running scheduled idempotency record cleanup...");
        return idempotencyService.cleanupExpiredRecords();
    }
}
