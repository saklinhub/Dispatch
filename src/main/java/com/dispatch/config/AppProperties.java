package com.dispatch.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "dispatch")
public class AppProperties {

    private final Jwt jwt = new Jwt();
    private final Rabbitmq rabbitmq = new Rabbitmq();
    private final Mail mail = new Mail();
    private final Sms sms = new Sms();
    private final Outbox outbox = new Outbox();
    private final Recovery recovery = new Recovery();

    @Data
    public static class Jwt {
        private String secret;
        private long expirationMs = 86_400_000L;
        private long refreshExpirationMs = 604_800_000L;
    }

    @Data
    public static class Rabbitmq {
        private String notificationExchange = "dispatch.notifications";
        private String notificationRoutingKey = "notification.process";
        private String notificationQueue = "dispatch.notifications.queue";
        private String dlqExchange = "dispatch.dlq";
        private String dlqQueue = "dispatch.dlq.queue";
        private String outboxExchange = "dispatch.outbox";
        private String outboxRoutingKey = "outbox.publish";
        private String outboxQueue = "dispatch.outbox.queue";
        private int retryMaxAttempts = 3;
    }

    @Data
    public static class Mail {
        private String fromAddress = "noreply@dispatch.io";
        private String fromName = "Dispatch Notifications";
    }

    @Data
    public static class Sms {
        private String accountSid;
        private String authToken;
        private String fromNumber;
        private String messagingServiceSid;
        private boolean enabled = false;
    }

    @Data
    public static class Outbox {
        private long pollingIntervalMs = 5_000L;
        private int batchSize = 50;
    }

    @Data
    public static class Recovery {
        private int stuckThresholdMinutes = 15;
        private int retryBaseIntervalSeconds = 10;
        private int retryMultiplier = 2;
        private int retryMaxIntervalSeconds = 3600;
        private long retryPollIntervalMs = 5000L;
        private long checkIntervalMs = 60000L;
    }
}
