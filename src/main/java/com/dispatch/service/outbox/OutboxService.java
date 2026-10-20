package com.dispatch.service.outbox;

import com.dispatch.dto.response.OutboxEventResponse;
import com.dispatch.dto.response.OutboxStatsResponse;
import com.dispatch.dto.response.PageResponse;
import com.dispatch.entity.enums.OutboxEventStatus;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface OutboxService {

    PageResponse<OutboxEventResponse> listEvents(OutboxEventStatus status, Pageable pageable);

    OutboxStatsResponse getStats();

    OutboxEventResponse retryEvent(UUID eventId);
}
