package com.dispatch.delivery;

import com.dispatch.dto.message.NotificationMessage;
import com.dispatch.entity.enums.NotificationType;

/**
 * Strategy interface for delivering notifications across distinct channels (EMAIL, SMS, IN_APP).
 */
public interface DeliveryStrategy {

    /**
     * The notification channel supported by this strategy.
     */
    NotificationType supportedType();

    /**
     * Dispatches the notification message through the underlying provider.
     */
    DeliveryResult deliver(NotificationMessage message);
}
