package com.dispatch.delivery.strategy;

import com.dispatch.config.AppProperties;
import com.dispatch.delivery.DeliveryResult;
import com.dispatch.delivery.DeliveryStrategy;
import com.dispatch.dto.message.NotificationMessage;
import com.dispatch.entity.DeveloperChannelCredential;
import com.dispatch.entity.enums.NotificationType;
import com.dispatch.service.DeveloperCredentialService;
import com.twilio.Twilio;
import com.twilio.exception.ApiException;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Strategy implementation for delivering SMS notifications via Twilio.
 * Supports multi-tenant developer-specific credentials as well as system defaults.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SmsDeliveryStrategy implements DeliveryStrategy {

    private final AppProperties appProperties;
    private final DeveloperCredentialService developerCredentialService;

    @Override
    public NotificationType supportedType() {
        return NotificationType.SMS;
    }

    @Override
    public DeliveryResult deliver(NotificationMessage message) {
        AppProperties.Sms smsConfig = appProperties.getSms();

        // Check for developer custom Twilio credentials
        Optional<DeveloperChannelCredential> devCredOpt = message.getUserId() != null
                ? developerCredentialService.findActiveCredential(message.getUserId(), NotificationType.SMS)
                : Optional.empty();

        String accountSid = null;
        String authToken = null;
        String providerLabel = "System Twilio";

        if (devCredOpt.isPresent()) {
            DeveloperChannelCredential cred = devCredOpt.get();
            String provider = cred.getProvider() != null ? cred.getProvider().toUpperCase() : "TWILIO";
            if ("SIMULATION".equalsIgnoreCase(provider)) {
                accountSid = null; // Simulation mode
                providerLabel = "Developer custom (" + provider + ")";
            } else {
                accountSid = (String) cred.getCredentialsJson().get("accountSid");
                authToken = (String) cred.getCredentialsJson().get("authToken");
                providerLabel = "Developer custom Twilio (" + cred.getProvider() + ")";
            }
        } else if (smsConfig.isEnabled()) {
            accountSid = smsConfig.getAccountSid();
            authToken = smsConfig.getAuthToken();
        }

        DeveloperChannelCredential devCred = devCredOpt.orElse(null);
        String fromNumber = resolveFromNumber(message, smsConfig, devCred);
        String messagingServiceSid = resolveMessagingServiceSid(message, smsConfig, devCred);

        // If credentials not configured, simulate delivery
        if (accountSid == null || accountSid.isBlank() || authToken == null || authToken.isBlank()) {
            String simulatedSid = "SM_SIMULATED_" + UUID.randomUUID().toString().replace("-", "");
            log.info("[SIMULATED SMS] From: {} | To: {} | Body: '{}' (Generated SID: {})",
                    messagingServiceSid != null ? "Service:" + messagingServiceSid : (fromNumber != null ? fromNumber : "DEFAULT"),
                    message.getRecipientAddress(),
                    message.getBody(),
                    simulatedSid);
            return DeliveryResult.success("Simulated SMS delivered. SID: " + simulatedSid);
        }

        try {
            Twilio.init(accountSid, authToken);

            PhoneNumber to = new PhoneNumber(message.getRecipientAddress());
            Message twilioMessage;

            if (messagingServiceSid != null && !messagingServiceSid.isBlank()) {
                twilioMessage = Message.creator(to, messagingServiceSid, message.getBody()).create();
            } else {
                PhoneNumber from = new PhoneNumber(fromNumber);
                twilioMessage = Message.creator(to, from, message.getBody()).create();
            }

            log.info("Sent SMS notification {} to {} via [{}] SID {}",
                    message.getNotificationId(), message.getRecipientAddress(), providerLabel, twilioMessage.getSid());
            return DeliveryResult.success("Twilio Message SID: " + twilioMessage.getSid() + " [" + providerLabel + "]");

        } catch (ApiException ex) {
            log.error("Twilio API failure for notification {}: [Code {}] {}",
                    message.getNotificationId(), ex.getCode(), ex.getMessage());
            return DeliveryResult.failure("TWILIO_" + ex.getCode(), ex.getMessage());
        } catch (Exception ex) {
            log.error("Failed to send SMS notification {}: {}", message.getNotificationId(), ex.getMessage());
            return DeliveryResult.failure("SMS_SEND_FAILED", ex.getMessage());
        }
    }

    private String resolveFromNumber(NotificationMessage message, AppProperties.Sms smsConfig, DeveloperChannelCredential devCred) {
        if (message.getChannelMetadata() != null) {
            Object from = message.getChannelMetadata().get("fromNumber");
            if (from == null) from = message.getChannelMetadata().get("from_number");
            if (from == null) from = message.getChannelMetadata().get("from");
            if (from != null && !from.toString().isBlank()) {
                return from.toString().trim();
            }
        }
        if (devCred != null && devCred.getCredentialsJson() != null) {
            Object from = devCred.getCredentialsJson().get("fromNumber");
            if (from != null && !from.toString().isBlank()) {
                return from.toString().trim();
            }
        }
        return smsConfig.getFromNumber();
    }

    private String resolveMessagingServiceSid(NotificationMessage message, AppProperties.Sms smsConfig, DeveloperChannelCredential devCred) {
        if (message.getChannelMetadata() != null) {
            Object sid = message.getChannelMetadata().get("messagingServiceSid");
            if (sid == null) sid = message.getChannelMetadata().get("messaging_service_sid");
            if (sid != null && !sid.toString().isBlank()) {
                return sid.toString().trim();
            }
        }
        if (devCred != null && devCred.getCredentialsJson() != null) {
            Object sid = devCred.getCredentialsJson().get("messagingServiceSid");
            if (sid != null && !sid.toString().isBlank()) {
                return sid.toString().trim();
            }
        }
        return smsConfig.getMessagingServiceSid();
    }
}
