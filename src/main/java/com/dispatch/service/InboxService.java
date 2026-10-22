package com.dispatch.service;

import com.dispatch.dto.response.InboxMessageResponse;
import com.dispatch.dto.response.PageResponse;
import com.dispatch.dto.response.UnreadCountResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Service interface for managing user in-app inbox messages.
 */
public interface InboxService {

    PageResponse<InboxMessageResponse> getInbox(UUID userId, Boolean read, Pageable pageable);

    UnreadCountResponse getUnreadCount(UUID userId);

    InboxMessageResponse markAsRead(UUID userId, UUID messageId);

    InboxMessageResponse markAsUnread(UUID userId, UUID messageId);

    int markAllAsRead(UUID userId);

    void deleteMessage(UUID userId, UUID messageId);
}
