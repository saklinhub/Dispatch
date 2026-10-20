package com.dispatch.mapper;

import com.dispatch.dto.response.DeliveryAttemptResponse;
import com.dispatch.dto.response.NotificationResponse;
import com.dispatch.dto.response.NotificationSummary;
import com.dispatch.dto.response.PageResponse;
import com.dispatch.entity.DeliveryAttempt;
import com.dispatch.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Converts JPA entities to response DTOs.
 * Kept as a plain Spring component to avoid MapStruct compile-time complexity.
 */
@Component
public class NotificationMapper {

    public NotificationResponse toResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .userId(n.getUser().getId())
                .type(n.getType())
                .status(n.getStatus())
                .subject(n.getSubject())
                .body(n.getBody())
                .recipientAddress(n.getRecipientAddress())
                .idempotencyKey(n.getIdempotencyKey())
                .channelMetadata(n.getChannelMetadata())
                .scheduledAt(n.getScheduledAt())
                .createdAt(n.getCreatedAt())
                .updatedAt(n.getUpdatedAt())
                .deliveryAttempts(n.getDeliveryAttempts()
                        .stream()
                        .map(this::toAttemptResponse)
                        .toList())
                .build();
    }

    public NotificationSummary toSummary(Notification n) {
        return NotificationSummary.builder()
                .id(n.getId())
                .type(n.getType())
                .status(n.getStatus())
                .subject(n.getSubject())
                .recipientAddress(n.getRecipientAddress())
                .scheduledAt(n.getScheduledAt())
                .createdAt(n.getCreatedAt())
                .updatedAt(n.getUpdatedAt())
                .build();
    }

    public DeliveryAttemptResponse toAttemptResponse(DeliveryAttempt da) {
        return DeliveryAttemptResponse.builder()
                .id(da.getId())
                .attemptNumber(da.getAttemptNumber())
                .status(da.getStatus())
                .providerResponse(da.getProviderResponse())
                .errorCode(da.getErrorCode())
                .errorMessage(da.getErrorMessage())
                .attemptedAt(da.getAttemptedAt())
                .nextRetryAt(da.getNextRetryAt())
                .createdAt(da.getCreatedAt())
                .build();
    }

    public <T> PageResponse<T> toPageResponse(Page<T> page) {
        return PageResponse.<T>builder()
                .content(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }
}
