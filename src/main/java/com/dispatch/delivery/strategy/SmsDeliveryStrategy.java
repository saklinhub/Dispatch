package com.dispatch.delivery.strategy;

import com.dispatch.config.AppProperties;
import com.dispatch.delivery.DeliveryResult;
import com.dispatch.delivery.DeliveryStrategy;
import com.dispatch.dto.message.NotificationMessage;
import com.dispatch.entity.enums.NotificationType;
import com.twilio.Twilio;
import com.twilio.exception.ApiException;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Strategy implementation for delivering SMS notifications via Twilio.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SmsDeliveryStrategy implements DeliveryStrategy {

    private final AppProperties appProperties;

    @Override
    public NotificationType supportedType() {
        return NotificationType.SMS;
    }

    @Override
    public DeliveryResult deliver(NotificationMessage message) {
        AppProperties.Sms smsConfig = appProperties.getSms();

        // If SMS is not enabled or credentials not configured, simulate delivery
        if (!smsConfig.isEnabled() || smsConfig.getAccountSid() == null || smsConfig.getAccountSid().isBlank()) {
            String simulatedSid = "SM_SIMULATED_" + UUID.randomUUID().toString().replace("-", "");
            log.info("[SIMULATED SMS] To: {} | Body: '{}' (Generated SID: {})",
                    message.getRecipientAddress(), message.getBody(), simulatedSid);
            return DeliveryResult.success("Simulated SMS delivered. SID: " + simulatedSid);
        }

        try {
            Twilio.init(smsConfig.getAccountSid(), smsConfig.getAuthToken());

            PhoneNumber to = new PhoneNumber(message.getRecipientAddress());
            Message twilioMessage;

            if (smsConfig.getMessagingServiceSid() != null && !smsConfig.getMessagingServiceSid().isBlank()) {
                twilioMessage = Message.creator(to, smsConfig.getMessagingServiceSid(), message.getBody()).create();
            } else {
                PhoneNumber from = new PhoneNumber(smsConfig.getFromNumber());
                twilioMessage = Message.creator(to, from, message.getBody()).create();
            }

            log.info("Sent SMS notification {} to {} via Twilio SID {}",
                    message.getNotificationId(), message.getRecipientAddress(), twilioMessage.getSid());
            return DeliveryResult.success("Twilio Message SID: " + twilioMessage.getSid());

        } catch (ApiException ex) {
            log.error("Twilio API failure for notification {}: [Code {}] {}",
                    message.getNotificationId(), ex.getCode(), ex.getMessage());
            return DeliveryResult.failure("TWILIO_" + ex.getCode(), ex.getMessage());
        } catch (Exception ex) {
            log.error("Failed to send SMS notification {}: {}", message.getNotificationId(), ex.getMessage());
            return DeliveryResult.failure("SMS_SEND_FAILED", ex.getMessage());
        }
    }
}
