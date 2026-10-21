package com.dispatch.service.idempotency;

import com.dispatch.config.AppProperties;
import com.dispatch.dto.request.CreateNotificationRequest;
import com.dispatch.dto.response.IdempotentExecutionResult;
import com.dispatch.dto.response.NotificationResponse;
import com.dispatch.entity.IdempotencyRecord;
import com.dispatch.entity.enums.IdempotencyStatus;
import com.dispatch.entity.enums.NotificationStatus;
import com.dispatch.entity.enums.NotificationType;
import com.dispatch.exception.IdempotencyConflictException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private IdempotencyStorage storage;

    @Mock
    private com.dispatch.repository.NotificationRepository notificationRepository;

    private AppProperties appProperties;
    private ObjectMapper objectMapper;
    private IdempotencyServiceImpl idempotencyService;

    private UUID userId;
    private CreateNotificationRequest request;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        appProperties.getIdempotency().setLockTimeoutSeconds(60L);
        appProperties.getIdempotency().setRetentionHours(24L);

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        idempotencyService = new IdempotencyServiceImpl(storage, notificationRepository, appProperties, objectMapper);

        userId = UUID.randomUUID();
        request = CreateNotificationRequest.builder()
                .type(NotificationType.EMAIL)
                .recipientAddress("user@example.com")
                .subject("Test Subject")
                .body("Hello World")
                .channelMetadata(Map.of("template", "welcome"))
                .build();
    }

    @Test
    @DisplayName("computeHash generates deterministic SHA-256 hash that changes when payload changes")
    void testComputeHashDeterminism() {
        String hash1 = idempotencyService.computeHash(userId, request);
        String hash2 = idempotencyService.computeHash(userId, request);
        assertEquals(hash1, hash2, "Identical payloads must generate the same hash");

        CreateNotificationRequest differentRequest = CreateNotificationRequest.builder()
                .type(NotificationType.EMAIL)
                .recipientAddress("user@example.com")
                .subject("Test Subject")
                .body("Different Body")
                .build();

        String hash3 = idempotencyService.computeHash(userId, differentRequest);
        assertNotEquals(hash1, hash3, "Different payloads must generate different hashes");
    }

    @Test
    @DisplayName("execute without key passes through directly without storage calls")
    void testExecuteWithoutKey() {
        NotificationResponse mockResponse = NotificationResponse.builder()
                .id(UUID.randomUUID())
                .status(NotificationStatus.PENDING)
                .build();

        AtomicInteger callCount = new AtomicInteger();
        IdempotentExecutionResult<NotificationResponse> result = idempotencyService.execute(
                null, userId, request, () -> {
                    callCount.incrementAndGet();
                    return mockResponse;
                });

        assertEquals(1, callCount.get());
        assertFalse(result.isReplayed());
        assertEquals(201, result.getStatusCode());
        verifyNoInteractions(storage);
    }

    @Test
    @DisplayName("Cached COMPLETED record returns cached response with replayed=true without invoking action")
    void testCachedCompletedReplay() throws Exception {
        String key = "test-key-123";
        String hash = idempotencyService.computeHash(userId, request);

        NotificationResponse cachedResponse = NotificationResponse.builder()
                .id(UUID.randomUUID())
                .status(NotificationStatus.PENDING)
                .subject("Cached Subject")
                .build();

        IdempotencyRecord completedRecord = IdempotencyRecord.builder()
                .idempotencyKey(key)
                .requestHash(hash)
                .status(IdempotencyStatus.COMPLETED)
                .responseStatusCode(201)
                .responseBody(objectMapper.writeValueAsString(cachedResponse))
                .lockedUntil(Instant.now().plusSeconds(60))
                .build();

        when(storage.findByKey(key)).thenReturn(Optional.of(completedRecord));

        AtomicInteger callCount = new AtomicInteger();
        IdempotentExecutionResult<NotificationResponse> result = idempotencyService.execute(
                key, userId, request, () -> {
                    callCount.incrementAndGet();
                    return null;
                });

        assertEquals(0, callCount.get(), "Action supplier must not be called on cached replay");
        assertTrue(result.isReplayed());
        assertEquals(cachedResponse.getId(), result.getData().getId());
        assertEquals("Cached Subject", result.getData().getSubject());
    }

    @Test
    @DisplayName("Payload mismatch throws IdempotencyConflictException (409)")
    void testPayloadMismatchThrowsConflict() {
        String key = "test-key-123";
        IdempotencyRecord recordWithDifferentHash = IdempotencyRecord.builder()
                .idempotencyKey(key)
                .requestHash("different-hash-value-1234567890abcdef")
                .status(IdempotencyStatus.COMPLETED)
                .build();

        when(storage.findByKey(key)).thenReturn(Optional.of(recordWithDifferentHash));

        IdempotencyConflictException ex = assertThrows(IdempotencyConflictException.class, () ->
                idempotencyService.execute(key, userId, request, () -> null)
        );

        assertTrue(ex.getMessage().contains("already used with a different request payload"));
    }

    @Test
    @DisplayName("Active IN_PROGRESS request within lock period throws IdempotencyConflictException (409)")
    void testActiveInProgressThrowsConflict() {
        String key = "test-key-123";
        String hash = idempotencyService.computeHash(userId, request);

        IdempotencyRecord inProgressRecord = IdempotencyRecord.builder()
                .idempotencyKey(key)
                .requestHash(hash)
                .status(IdempotencyStatus.IN_PROGRESS)
                .lockedUntil(Instant.now().plusSeconds(30))
                .build();

        when(storage.findByKey(key)).thenReturn(Optional.of(inProgressRecord));

        IdempotencyConflictException ex = assertThrows(IdempotencyConflictException.class, () ->
                idempotencyService.execute(key, userId, request, () -> null)
        );

        assertTrue(ex.getMessage().contains("currently being processed"));
    }

    @Test
    @DisplayName("Failed execution updates record to FAILED status and rethrows error")
    void testFailedExecutionUpdatesStatus() {
        String key = "fail-key";
        when(storage.findByKey(key)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () ->
                idempotencyService.execute(key, userId, request, () -> {
                    throw new IllegalStateException("Simulated processing failure");
                })
        );

        verify(storage).markFailed(key);
    }
}
