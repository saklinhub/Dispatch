package com.dispatch.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ Broker Topology configuration.
 * Declares exchanges, queues, dead-letter routing, bindings, and JSON serialization.
 */
@Configuration
@RequiredArgsConstructor
public class RabbitMQConfig {

    private final AppProperties appProperties;

    public static final String DLQ_ROUTING_KEY = "notification.dlq";

    // --- Exchanges ---

    @Bean
    public DirectExchange notificationExchange() {
        return new DirectExchange(appProperties.getRabbitmq().getNotificationExchange(), true, false);
    }

    @Bean
    public DirectExchange dlqExchange() {
        return new DirectExchange(appProperties.getRabbitmq().getDlqExchange(), true, false);
    }

    @Bean
    public TopicExchange outboxExchange() {
        return new TopicExchange(appProperties.getRabbitmq().getOutboxExchange(), true, false);
    }

    // --- Queues ---

    /**
     * Primary processing queue.
     * Configured with Dead-Letter Exchange (DLQ) arguments for exhausted retry routing.
     */
    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable(appProperties.getRabbitmq().getNotificationQueue())
                .withArgument("x-dead-letter-exchange", appProperties.getRabbitmq().getDlqExchange())
                .withArgument("x-dead-letter-routing-key", DLQ_ROUTING_KEY)
                .build();
    }

    /**
     * Dead-Letter Queue (DLQ) for inspecting and manual replays of failed messages.
     */
    @Bean
    public Queue dlqQueue() {
        return QueueBuilder.durable(appProperties.getRabbitmq().getDlqQueue()).build();
    }

    /**
     * Outbox event queue for subscribers of transactional outbox notifications.
     */
    @Bean
    public Queue outboxQueue() {
        return QueueBuilder.durable(appProperties.getRabbitmq().getOutboxQueue()).build();
    }

    // --- Bindings ---

    @Bean
    public Binding notificationBinding(Queue notificationQueue, DirectExchange notificationExchange) {
        return BindingBuilder
                .bind(notificationQueue)
                .to(notificationExchange)
                .with(appProperties.getRabbitmq().getNotificationRoutingKey());
    }

    @Bean
    public Binding dlqBinding(Queue dlqQueue, DirectExchange dlqExchange) {
        return BindingBuilder
                .bind(dlqQueue)
                .to(dlqExchange)
                .with(DLQ_ROUTING_KEY);
    }

    @Bean
    public Binding outboxBinding(Queue outboxQueue, TopicExchange outboxExchange) {
        return BindingBuilder
                .bind(outboxQueue)
                .to(outboxExchange)
                .with(appProperties.getRabbitmq().getOutboxRoutingKey());
    }

    // --- Converters & RabbitTemplate ---

    @Bean
    public MessageConverter jsonMessageConverter() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }

    @Bean
    public org.springframework.amqp.rabbit.core.RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new org.springframework.amqp.rabbit.core.RabbitAdmin(connectionFactory);
    }
}
