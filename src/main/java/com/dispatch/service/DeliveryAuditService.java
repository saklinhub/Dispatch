package com.dispatch.service;

import com.dispatch.dto.response.DeliveryAttemptResponse;
import com.dispatch.dto.response.PageResponse;
import com.dispatch.entity.enums.AttemptStatus;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Service interface for querying delivery attempt history and audit details.
 */
public interface DeliveryAuditService {

    PageResponse<DeliveryAttemptResponse> getUserDeliveryHistory(UUID userId, AttemptStatus status, Pageable pageable);

    DeliveryAttemptResponse getAttemptById(UUID attemptId);
}
