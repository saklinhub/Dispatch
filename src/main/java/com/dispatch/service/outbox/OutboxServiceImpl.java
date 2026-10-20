package com.dispatch.service.outbox;

import com.dispatch.dto.response.OutboxEventResponse;
import com.dispatch.dto.response.OutboxStatsResponse;
import com.dispatch.dto.response.PageResponse;
import com.dispatch.entity.OutboxEvent;
import com.dispatch.entity.enums.OutboxEventStatus;
import com.dispatch.exception.ResourceNotFoundException;
import com.dispatch.mapper.OutboxMapper;
import com.dispatch.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OutboxServiceImpl implements OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxPublisher outboxPublisher;
    private final OutboxMapper mapper;

    @Override
    public PageResponse<OutboxEventResponse> listEvents(OutboxEventStatus status, Pageable pageable) {
        Page<OutboxEvent> page;
        if (status != null) {
            List<OutboxEvent> list = outboxEventRepository.findByStatusOrderByCreatedAtAsc(status, pageable);
            long total = outboxEventRepository.countByStatus(status);
            page = new PageImpl<>(list, pageable, total);
        } else {
            page = outboxEventRepository.findAll(pageable);
        }

        List<OutboxEventResponse> content = page.getContent().stream()
                .map(mapper::toResponse)
                .toList();

        return PageResponse.<OutboxEventResponse>builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    @Override
    public OutboxStatsResponse getStats() {
        long pending = outboxEventRepository.countByStatus(OutboxEventStatus.PENDING);
        long published = outboxEventRepository.countByStatus(OutboxEventStatus.PUBLISHED);
        long failed = outboxEventRepository.countByStatus(OutboxEventStatus.FAILED);
        long total = outboxEventRepository.count();

        return OutboxStatsResponse.builder()
                .pendingCount(pending)
                .publishedCount(published)
                .failedCount(failed)
                .totalCount(total)
                .build();
    }

    @Override
    @Transactional
    public OutboxEventResponse retryEvent(UUID eventId) {
        OutboxEvent event = outboxEventRepository.findById(eventId)
                .orElseThrow(() -> ResourceNotFoundException.outboxEvent(eventId.toString()));

        log.info("Manually re-triggering outbox event {}", eventId);
        outboxPublisher.publish(event);
        return mapper.toResponse(event);
    }
}
