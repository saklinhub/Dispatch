package com.dispatch.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RabbitMQConfigTest {

    @Autowired
    private DirectExchange notificationExchange;

    @Autowired
    private DirectExchange dlqExchange;

    @Autowired
    private TopicExchange outboxExchange;

    @Autowired
    private Queue notificationQueue;

    @Autowired
    private Queue dlqQueue;

    @Autowired
    private Queue outboxQueue;

    @Autowired
    private Binding notificationBinding;

    @Autowired
    private Binding dlqBinding;

    @Autowired
    private Binding outboxBinding;

    @Autowired
    private AppProperties appProperties;

    @Test
    @DisplayName("Verify RabbitMQ exchanges are declared with durable settings")
    void testExchangesDeclared() {
        assertEquals(appProperties.getRabbitmq().getNotificationExchange(), notificationExchange.getName());
        assertTrue(notificationExchange.isDurable());

        assertEquals(appProperties.getRabbitmq().getDlqExchange(), dlqExchange.getName());
        assertTrue(dlqExchange.isDurable());

        assertEquals(appProperties.getRabbitmq().getOutboxExchange(), outboxExchange.getName());
        assertTrue(outboxExchange.isDurable());
    }

    @Test
    @DisplayName("Verify notificationQueue is configured with DLQ arguments")
    void testNotificationQueueDLQArguments() {
        assertEquals(appProperties.getRabbitmq().getNotificationQueue(), notificationQueue.getName());
        assertTrue(notificationQueue.isDurable());

        Object dlx = notificationQueue.getArguments().get("x-dead-letter-exchange");
        assertEquals(appProperties.getRabbitmq().getDlqExchange(), dlx);

        Object dlrk = notificationQueue.getArguments().get("x-dead-letter-routing-key");
        assertEquals(RabbitMQConfig.DLQ_ROUTING_KEY, dlrk);
    }

    @Test
    @DisplayName("Verify bindings route correctly")
    void testBindings() {
        assertEquals(appProperties.getRabbitmq().getNotificationRoutingKey(), notificationBinding.getRoutingKey());
        assertEquals(RabbitMQConfig.DLQ_ROUTING_KEY, dlqBinding.getRoutingKey());
        assertEquals(appProperties.getRabbitmq().getOutboxRoutingKey(), outboxBinding.getRoutingKey());
    }
}
