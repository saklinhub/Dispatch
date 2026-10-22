package com.dispatch.service.impl;

import com.dispatch.dto.response.InboxMessageResponse;
import com.dispatch.dto.response.PageResponse;
import com.dispatch.dto.response.UnreadCountResponse;
import com.dispatch.entity.InboxMessage;
import com.dispatch.exception.ResourceNotFoundException;
import com.dispatch.mapper.InboxMapper;
import com.dispatch.repository.InboxMessageRepository;
import com.dispatch.repository.UserRepository;
import com.dispatch.service.InboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Production implementation of InboxService managing user in-app notifications.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InboxServiceImpl implements InboxService {

    private final InboxMessageRepository inboxMessageRepository;
    private final UserRepository userRepository;
    private final InboxMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<InboxMessageResponse> getInbox(UUID userId, Boolean read, Pageable pageable) {
        ensureUserExists(userId);

        Pageable effectivePageable = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<InboxMessage> page;
        if (read == null) {
            page = inboxMessageRepository.findByUserId(userId, effectivePageable);
        } else {
            page = inboxMessageRepository.findByUserIdAndRead(userId, read, effectivePageable);
        }

        Page<InboxMessageResponse> mappedPage = page.map(mapper::toResponse);
        return mapper.toPageResponse(mappedPage);
    }

    @Override
    @Transactional(readOnly = true)
    public UnreadCountResponse getUnreadCount(UUID userId) {
        ensureUserExists(userId);
        long count = inboxMessageRepository.countByUserIdAndReadFalse(userId);
        return UnreadCountResponse.builder()
                .userId(userId)
                .unreadCount(count)
                .build();
    }

    @Override
    @Transactional
    public InboxMessageResponse markAsRead(UUID userId, UUID messageId) {
        ensureUserExists(userId);
        InboxMessage message = inboxMessageRepository.findByIdAndUserId(messageId, userId)
                .orElseThrow(() -> ResourceNotFoundException.inboxMessage(messageId.toString()));

        if (!message.isRead()) {
            message.setRead(true);
            message.setReadAt(Instant.now());
            message = inboxMessageRepository.save(message);
            log.info("Marked inbox message {} as read for user {}", messageId, userId);
        }
        return mapper.toResponse(message);
    }

    @Override
    @Transactional
    public InboxMessageResponse markAsUnread(UUID userId, UUID messageId) {
        ensureUserExists(userId);
        InboxMessage message = inboxMessageRepository.findByIdAndUserId(messageId, userId)
                .orElseThrow(() -> ResourceNotFoundException.inboxMessage(messageId.toString()));

        if (message.isRead()) {
            message.setRead(false);
            message.setReadAt(null);
            message = inboxMessageRepository.save(message);
            log.info("Marked inbox message {} as unread for user {}", messageId, userId);
        }
        return mapper.toResponse(message);
    }

    @Override
    @Transactional
    public int markAllAsRead(UUID userId) {
        ensureUserExists(userId);
        int updated = inboxMessageRepository.markAllAsReadForUser(userId, Instant.now());
        log.info("Marked all ({}) inbox messages as read for user {}", updated, userId);
        return updated;
    }

    @Override
    @Transactional
    public void deleteMessage(UUID userId, UUID messageId) {
        ensureUserExists(userId);
        InboxMessage message = inboxMessageRepository.findByIdAndUserId(messageId, userId)
                .orElseThrow(() -> ResourceNotFoundException.inboxMessage(messageId.toString()));

        inboxMessageRepository.delete(message);
        log.info("Deleted inbox message {} for user {}", messageId, userId);
    }

    private void ensureUserExists(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw ResourceNotFoundException.user(userId.toString());
        }
    }
}
