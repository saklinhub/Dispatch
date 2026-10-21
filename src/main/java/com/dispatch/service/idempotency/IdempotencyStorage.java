package com.dispatch.service.idempotency;

import com.dispatch.entity.IdempotencyRecord;
import com.dispatch.entity.enums.IdempotencyStatus;
import com.dispatch.repository.IdempotencyRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Isolated transactional storage layer for idempotency records.
 * Runs lock operations in REQUIRES_NEW transactions to guarantee immediate broker/database visibility.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyStorage {

    private final IdempotencyRecordRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<IdempotencyRecord> findByKey(String key) {
        return repository.findByIdempotencyKey(key);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IdempotencyRecord createInProgress(String key, String requestHash, Instant lockedUntil) {
        IdempotencyRecord record = IdempotencyRecord.builder()
                .idempotencyKey(key)
                .requestHash(requestHash)
                .status(IdempotencyStatus.IN_PROGRESS)
                .lockedUntil(lockedUntil)
                .build();
        return repository.saveAndFlush(record);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateLock(String key, Instant lockedUntil) {
        repository.findByIdempotencyKey(key).ifPresent(record -> {
            record.setStatus(IdempotencyStatus.IN_PROGRESS);
            record.setLockedUntil(lockedUntil);
            repository.saveAndFlush(record);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompleted(String key, int statusCode, String responseJson, java.util.UUID notificationId) {
        repository.findByIdempotencyKey(key).ifPresent(record -> {
            record.setStatus(IdempotencyStatus.COMPLETED);
            record.setResponseStatusCode(statusCode);
            record.setResponseBody(responseJson);
            record.setNotificationId(notificationId);
            repository.saveAndFlush(record);
            log.debug("Marked idempotency key '{}' as COMPLETED", key);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(String key) {
        repository.findByIdempotencyKey(key).ifPresent(record -> {
            record.setStatus(IdempotencyStatus.FAILED);
            repository.saveAndFlush(record);
            log.debug("Marked idempotency key '{}' as FAILED", key);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteKey(String key) {
        repository.deleteByIdempotencyKey(key);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int purgeExpired(Instant cutoff) {
        return repository.deleteExpiredRecords(cutoff);
    }
}
