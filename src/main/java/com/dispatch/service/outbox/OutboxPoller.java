package com.dispatch.service.outbox;

import com.dispatch.config.AppProperties;
import com.dispatch.entity.OutboxEvent;
import com.dispatch.entity.enums.OutboxEventStatus;
import com.dispatch.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scheduled background worker that polls the transactional outbox table for pending events
 * and relays them into RabbitMQ via OutboxPublisher.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPoller {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxPublisher outboxPublisher;
    private final AppProperties appProperties;

    @Scheduled(fixedDelayString = "${dispatch.outbox.polling-interval-ms:5000}")
    public void pollAndPublish() {
        int batchSize = appProperties.getOutbox().getBatchSize();
        List<OutboxEvent> pendingEvents = outboxEventRepository.findByStatusOrderByCreatedAtAsc(
                OutboxEventStatus.PENDING, PageRequest.of(0, batchSize));

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.debug("Found {} pending outbox event(s) to publish", pendingEvents.size());

        int publishedCount = 0;
        int failedCount = 0;

        for (OutboxEvent event : pendingEvents) {
            boolean success = outboxPublisher.publish(event);
            if (success) {
                publishedCount++;
            } else {
                failedCount++;
            }
        }

        log.info("Outbox poller cycle completed: {} published, {} failed", publishedCount, failedCount);
    }
}
