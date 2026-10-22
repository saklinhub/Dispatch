package com.dispatch.mapper;

import com.dispatch.dto.response.InboxMessageResponse;
import com.dispatch.dto.response.PageResponse;
import com.dispatch.entity.InboxMessage;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/**
 * Converts InboxMessage entities to response DTOs.
 */
@Component
public class InboxMapper {

    public InboxMessageResponse toResponse(InboxMessage message) {
        if (message == null) {
            return null;
        }
        return InboxMessageResponse.builder()
                .id(message.getId())
                .userId(message.getUser() != null ? message.getUser().getId() : null)
                .notificationId(message.getNotification() != null ? message.getNotification().getId() : null)
                .title(message.getTitle())
                .body(message.getBody())
                .read(message.isRead())
                .readAt(message.getReadAt())
                .createdAt(message.getCreatedAt())
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
