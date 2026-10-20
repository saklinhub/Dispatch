package com.dispatch.consumer;

import com.dispatch.delivery.DeliveryDispatcher;
import com.dispatch.dto.message.NotificationMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Asynchronous consumer listening on the primary notification queue.
 * Consumes messages published by the Transactional Outbox and triggers multi-channel dispatch.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationConsumer {

    private final DeliveryDispatcher deliveryDispatcher;

    @RabbitListener(queues = "${dispatch.rabbitmq.notification-queue:dispatch.notifications.queue}")
    public void consumeNotification(NotificationMessage message) {
        log.info("Consumer received notification message: [ID: {}, Type: {}, Recipient: {}]",
                message.getNotificationId(), message.getType(), message.getRecipientAddress());

        deliveryDispatcher.dispatch(message);
    }
}
