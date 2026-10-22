package com.dispatch.observability;

import com.dispatch.entity.enums.OutboxEventStatus;
import com.dispatch.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Custom Actuator Health Indicator for monitoring notification pipeline health,
 * outbox backlog levels, and database responsiveness.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationSystemHealthIndicator implements HealthIndicator {

    private static final long MAX_HEALTHY_OUTBOX_BACKLOG = 5000;

    private final OutboxEventRepository outboxEventRepository;

    @Override
    public Health health() {
        try {
            long pendingOutbox = outboxEventRepository.countByStatus(OutboxEventStatus.PENDING);
            long failedOutbox = outboxEventRepository.countByStatus(OutboxEventStatus.FAILED);

            Health.Builder builder;
            if (pendingOutbox > MAX_HEALTHY_OUTBOX_BACKLOG) {
                builder = Health.down()
                        .withDetail("warning", "High outbox backlog detected exceeding threshold (" + MAX_HEALTHY_OUTBOX_BACKLOG + ")");
            } else {
                builder = Health.up();
            }

            return builder
                    .withDetail("service", "Dispatch Notification Platform")
                    .withDetail("status", "OPERATIONAL")
                    .withDetail("outboxPendingEvents", pendingOutbox)
                    .withDetail("outboxFailedEvents", failedOutbox)
                    .build();
        } catch (Exception ex) {
            log.error("Notification health check failed: {}", ex.getMessage());
            return Health.down(ex)
                    .withDetail("service", "Dispatch Notification Platform")
                    .withDetail("error", ex.getMessage())
                    .build();
        }
    }
}
