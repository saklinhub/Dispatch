package com.dispatch.service.idempotency;

import com.dispatch.config.AppProperties;
import com.dispatch.dto.request.CreateNotificationRequest;
import com.dispatch.dto.response.IdempotentExecutionResult;
import com.dispatch.dto.response.IdempotencyResponse;
import com.dispatch.dto.response.NotificationResponse;
import com.dispatch.entity.IdempotencyRecord;
import com.dispatch.entity.enums.IdempotencyStatus;
import com.dispatch.exception.IdempotencyConflictException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyServiceImpl implements IdempotencyService {

    private final IdempotencyStorage storage;
    private final com.dispatch.repository.NotificationRepository notificationRepository;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    @Override
    public String computeHash(UUID userId, CreateNotificationRequest request) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder sb = new StringBuilder();
            sb.append(userId != null ? userId.toString() : "").append("|");
            sb.append(request.getType() != null ? request.getType().name() : "").append("|");
            sb.append(request.getRecipientAddress() != null ? request.getRecipientAddress().trim() : "").append("|");
            sb.append(request.getSubject() != null ? request.getSubject().trim() : "").append("|");
            sb.append(request.getBody() != null ? request.getBody().trim() : "").append("|");
            if (request.getChannelMetadata() != null && !request.getChannelMetadata().isEmpty()) {
                new TreeMap<>(request.getChannelMetadata()).forEach((k, v) -> sb.append(k).append("=").append(v).append(";"));
            }
            sb.append("|");
            if (request.getScheduledAt() != null) {
                sb.append(request.getScheduledAt().toString());
            }

            byte[] hashBytes = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    @Override
    public IdempotentExecutionResult<NotificationResponse> execute(
            String key,
            UUID userId,
            CreateNotificationRequest request,
            Supplier<NotificationResponse> action) {

        if (key == null || key.isBlank()) {
            NotificationResponse directResult = action.get();
            return new IdempotentExecutionResult<>(directResult, false, 201);
        }

        String trimmedKey = key.trim();
        String requestHash = computeHash(userId, request);
        long lockTimeout = appProperties.getIdempotency().getLockTimeoutSeconds();

        Optional<IdempotencyRecord> existingOpt = storage.findByKey(trimmedKey);

        if (existingOpt.isPresent()) {
            IdempotencyRecord existing = existingOpt.get();

            // 1. Verify payload fingerprint
            if (!existing.getRequestHash().equals(requestHash)) {
                log.warn("Payload mismatch for idempotency key '{}'", trimmedKey);
                throw new IdempotencyConflictException(
                        "Idempotency key '" + trimmedKey + "' was already used with a different request payload");
            }

            // 2. Cached completed response replay
            if (existing.getStatus() == IdempotencyStatus.COMPLETED) {
                log.info("Returning cached response for idempotency key '{}'", trimmedKey);
                try {
                    NotificationResponse cached = objectMapper.readValue(
                            existing.getResponseBody(), NotificationResponse.class);
                    int status = existing.getResponseStatusCode() != null ? existing.getResponseStatusCode() : 201;
                    return new IdempotentExecutionResult<>(cached, true, status);
                } catch (JsonProcessingException e) {
                    log.error("Failed to deserialize cached idempotency response: {}", e.getMessage());
                    throw new RuntimeException("Failed to read cached idempotency response", e);
                }
            }

            // 3. In-flight concurrent request check
            if (existing.getStatus() == IdempotencyStatus.IN_PROGRESS) {
                if (existing.getLockedUntil().isAfter(Instant.now())) {
                    log.warn("Concurrent in-progress request detected for key '{}'", trimmedKey);
                    throw new IdempotencyConflictException(
                            "A request with idempotency key '" + trimmedKey + "' is currently being processed. Please retry shortly.");
                } else {
                    log.info("Lock expired for key '{}'. Acquiring lock for retry.", trimmedKey);
                    storage.updateLock(trimmedKey, Instant.now().plusSeconds(lockTimeout));
                }
            } else if (existing.getStatus() == IdempotencyStatus.FAILED) {
                log.info("Previous attempt failed for key '{}'. Retrying execution.", trimmedKey);
                storage.updateLock(trimmedKey, Instant.now().plusSeconds(lockTimeout));
            }
        } else {
            // Register new IN_PROGRESS record
            try {
                storage.createInProgress(trimmedKey, requestHash, Instant.now().plusSeconds(lockTimeout));
            } catch (DataIntegrityViolationException ex) {
                // Concurrent insert race condition caught by unique constraint
                log.info("Concurrent insert race for idempotency key '{}'. Re-checking record.", trimmedKey);
                Optional<IdempotencyRecord> raced = storage.findByKey(trimmedKey);
                if (raced.isPresent()) {
                    IdempotencyRecord racedRecord = raced.get();
                    if (!racedRecord.getRequestHash().equals(requestHash)) {
                        throw new IdempotencyConflictException(
                                "Idempotency key '" + trimmedKey + "' was already used with a different request payload");
                    }
                    if (racedRecord.getStatus() == IdempotencyStatus.COMPLETED) {
                        try {
                            NotificationResponse cached = objectMapper.readValue(
                                    racedRecord.getResponseBody(), NotificationResponse.class);
                            return new IdempotentExecutionResult<>(cached, true, 201);
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException("Failed to deserialize cached response", e);
                        }
                    }
                }
                throw new IdempotencyConflictException(
                        "A request with idempotency key '" + trimmedKey + "' is currently being processed. Please retry shortly.");
            }
        }

        // Execute target action
        NotificationResponse result;
        try {
            result = action.get();
        } catch (Exception ex) {
            storage.markFailed(trimmedKey);
            throw ex;
        }

        // Cache completed response
        try {
            String json = objectMapper.writeValueAsString(result);
            storage.markCompleted(trimmedKey, 201, json, result.getId());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize notification response for caching: {}", e.getMessage());
        }

        return new IdempotentExecutionResult<>(result, false, 201);
    }

    @Override
    public Optional<IdempotencyResponse> getRecord(String key) {
        return storage.findByKey(key).map(r -> IdempotencyResponse.builder()
                .id(r.getId())
                .idempotencyKey(r.getIdempotencyKey())
                .requestHash(r.getRequestHash())
                .status(r.getStatus())
                .responseStatusCode(r.getResponseStatusCode())
                .notificationId(r.getNotificationId())
                .createdAt(r.getCreatedAt())
                .lockedUntil(r.getLockedUntil())
                .build());
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void deleteRecord(String key) {
        storage.deleteKey(key);
        notificationRepository.findByIdempotencyKey(key).ifPresent(n -> {
            n.setIdempotencyKey(null);
            notificationRepository.save(n);
        });
    }

    @Override
    public int cleanupExpiredRecords() {
        long retentionHours = appProperties.getIdempotency().getRetentionHours();
        Instant cutoff = Instant.now().minus(Duration.ofHours(retentionHours));
        int purged = storage.purgeExpired(cutoff);
        if (purged > 0) {
            log.info("Purged {} expired idempotency records older than {} hours", purged, retentionHours);
        }
        return purged;
    }
}
