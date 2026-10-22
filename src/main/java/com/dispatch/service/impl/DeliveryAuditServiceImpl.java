package com.dispatch.service.impl;

import com.dispatch.dto.response.DeliveryAttemptResponse;
import com.dispatch.dto.response.PageResponse;
import com.dispatch.entity.DeliveryAttempt;
import com.dispatch.entity.enums.AttemptStatus;
import com.dispatch.exception.ResourceNotFoundException;
import com.dispatch.mapper.NotificationMapper;
import com.dispatch.repository.DeliveryAttemptRepository;
import com.dispatch.repository.UserRepository;
import com.dispatch.service.DeliveryAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Production implementation of DeliveryAuditService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryAuditServiceImpl implements DeliveryAuditService {

    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final UserRepository userRepository;
    private final NotificationMapper notificationMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<DeliveryAttemptResponse> getUserDeliveryHistory(UUID userId, AttemptStatus status, Pageable pageable) {
        if (!userRepository.existsById(userId)) {
            throw ResourceNotFoundException.user(userId.toString());
        }

        Pageable effectivePageable = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "attemptedAt"));

        Page<DeliveryAttempt> page;
        if (status == null) {
            page = deliveryAttemptRepository.findByUserId(userId, effectivePageable);
        } else {
            page = deliveryAttemptRepository.findByUserIdAndStatus(userId, status, effectivePageable);
        }

        Page<DeliveryAttemptResponse> mappedPage = page.map(notificationMapper::toAttemptResponse);
        return notificationMapper.toPageResponse(mappedPage);
    }

    @Override
    @Transactional(readOnly = true)
    public DeliveryAttemptResponse getAttemptById(UUID attemptId) {
        DeliveryAttempt attempt = deliveryAttemptRepository.findById(attemptId)
                .orElseThrow(() -> ResourceNotFoundException.deliveryAttempt(attemptId.toString()));

        return notificationMapper.toAttemptResponse(attempt);
    }
}
