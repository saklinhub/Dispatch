package com.dispatch.observability;

import com.dispatch.entity.enums.NotificationType;
import com.dispatch.entity.enums.OutboxEventStatus;
import com.dispatch.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Micrometer metrics collector for Dispatch notification lifecycle and throughput.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationMetrics {

    private final MeterRegistry meterRegistry;
    private final OutboxEventRepository outboxEventRepository;

    @PostConstruct
    public void init() {
        Gauge.builder("dispatch.outbox.pending.gauge", outboxEventRepository,
                        repo -> repo.countByStatus(OutboxEventStatus.PENDING))
                .description("Current number of pending outbox events awaiting publication")
                .register(meterRegistry);
    }

    public void recordNotificationCreated(NotificationType type) {
        Counter.builder("dispatch.notifications.created")
                .tag("channel", type != null ? type.name() : "UNKNOWN")
                .description("Total number of notification requests created")
                .register(meterRegistry)
                .increment();
    }

    public void recordNotificationDelivered(NotificationType type, Duration duration) {
        Counter.builder("dispatch.notifications.delivered")
                .tag("channel", type != null ? type.name() : "UNKNOWN")
                .description("Total number of notifications successfully delivered")
                .register(meterRegistry)
                .increment();

        if (duration != null) {
            Timer.builder("dispatch.delivery.latency")
                    .tag("channel", type != null ? type.name() : "UNKNOWN")
                    .description("Delivery strategy latency timer")
                    .register(meterRegistry)
                    .record(duration.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    public void recordNotificationFailed(NotificationType type, String errorCode) {
        Counter.builder("dispatch.notifications.failed")
                .tag("channel", type != null ? type.name() : "UNKNOWN")
                .tag("error_code", errorCode != null ? errorCode : "UNKNOWN")
                .description("Total number of failed notification deliveries")
                .register(meterRegistry)
                .increment();
    }

    public void recordOutboxPublished() {
        Counter.builder("dispatch.outbox.published")
                .description("Total number of outbox events published to RabbitMQ")
                .register(meterRegistry)
                .increment();
    }
}
