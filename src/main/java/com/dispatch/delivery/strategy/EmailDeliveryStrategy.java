package com.dispatch.delivery.strategy;

import com.dispatch.config.AppProperties;
import com.dispatch.delivery.DeliveryResult;
import com.dispatch.delivery.DeliveryStrategy;
import com.dispatch.dto.message.NotificationMessage;
import com.dispatch.entity.DeveloperChannelCredential;
import com.dispatch.entity.enums.NotificationType;
import com.dispatch.service.DeveloperCredentialService;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * Strategy implementation for delivering notifications via SMTP Email.
 * Supports multi-tenant developer-specific credentials as well as system defaults.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailDeliveryStrategy implements DeliveryStrategy {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final AppProperties appProperties;
    private final DeveloperCredentialService developerCredentialService;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    @Override
    public NotificationType supportedType() {
        return NotificationType.EMAIL;
    }

    @Override
    public DeliveryResult deliver(NotificationMessage message) {
        // Check if the sending developer has registered custom email credentials (Multi-Tenant)
        Optional<DeveloperChannelCredential> devCredOpt = message.getUserId() != null
                ? developerCredentialService.findActiveCredential(message.getUserId(), NotificationType.EMAIL)
                : Optional.empty();

        JavaMailSender mailSender;
        String providerLabel;

        if (devCredOpt.isPresent()) {
            DeveloperChannelCredential cred = devCredOpt.get();
            String provider = cred.getProvider() != null ? cred.getProvider().toUpperCase() : "SMTP";
            String host = (String) cred.getCredentialsJson().getOrDefault("host", "");

            if ("SIMULATION".equalsIgnoreCase(provider) || "simulation".equalsIgnoreCase(host) || "localhost".equalsIgnoreCase(host)) {
                mailSender = null; // Developer custom simulation mode
                providerLabel = "Developer custom (" + provider + ")";
            } else {
                mailSender = buildDeveloperMailSender(cred.getCredentialsJson());
                providerLabel = "Developer custom (" + cred.getProvider() + ")";
            }
        } else {
            mailSender = mailSenderProvider.getIfAvailable();
            providerLabel = "System SMTP";
            if (mailSender == null || mailUsername.isBlank() || mailPassword.isBlank()) {
                mailSender = null; // System simulation mode
            }
        }

        // Dynamically resolve sender properties:
        // Priority 1: request channelMetadata overrides
        // Priority 2: developer saved credentials (if available)
        // Priority 3: system application default
        DeveloperChannelCredential devCred = devCredOpt.orElse(null);
        String fromAddress = resolveFromAddress(message, devCred);
        String fromName = resolveFromName(message, devCred);
        String replyTo = resolveReplyTo(message, devCred);

        // If credentials are not configured or mail sender is absent, simulate delivery
        if (mailSender == null) {
            log.info("[SIMULATED EMAIL] From: {} <{}> | To: {} | Subject: '{}' | Body: '{}'{}",
                    fromName != null ? fromName : fromAddress,
                    fromAddress,
                    message.getRecipientAddress(),
                    message.getSubject(),
                    message.getBody(),
                    replyTo != null ? " | Reply-To: " + replyTo : "");
            return DeliveryResult.success("Simulated email dispatched from " + fromAddress + " to " + message.getRecipientAddress());
        }

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, "UTF-8");

            if (fromName != null && !fromName.isBlank()) {
                helper.setFrom(fromAddress, fromName);
            } else {
                helper.setFrom(fromAddress);
            }

            if (replyTo != null && !replyTo.isBlank()) {
                helper.setReplyTo(replyTo);
            }

            helper.setTo(message.getRecipientAddress());
            helper.setSubject(message.getSubject() != null ? message.getSubject() : "Dispatch Notification");
            helper.setText(message.getBody(), false);

            mailSender.send(mimeMessage);
            log.info("Sent EMAIL notification {} from {} to {} via [{}]",
                    message.getNotificationId(), fromAddress, message.getRecipientAddress(), providerLabel);
            return DeliveryResult.success("SMTP 250 OK [" + providerLabel + "] - message queued from " + fromAddress + " for " + message.getRecipientAddress());

        } catch (Exception ex) {
            log.error("Failed to send EMAIL notification {}: {}", message.getNotificationId(), ex.getMessage());
            return DeliveryResult.failure("SMTP_SEND_FAILED", ex.getMessage());
        }
    }

    private JavaMailSender buildDeveloperMailSender(Map<String, Object> config) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        String host = (String) config.getOrDefault("host", "smtp.gmail.com");
        int port = 587;
        if (config.get("port") instanceof Number n) {
            port = n.intValue();
        }
        String username = (String) config.get("username");
        String password = (String) config.get("password");

        sender.setHost(host);
        sender.setPort(port);
        sender.setUsername(username);
        sender.setPassword(password);

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "5000");
        props.put("mail.smtp.writetimeout", "5000");

        return sender;
    }

    private String resolveFromAddress(NotificationMessage message, DeveloperChannelCredential devCred) {
        if (message.getChannelMetadata() != null) {
            Object from = message.getChannelMetadata().get("fromAddress");
            if (from == null) from = message.getChannelMetadata().get("from_address");
            if (from == null) from = message.getChannelMetadata().get("from");
            if (from != null && !from.toString().isBlank()) {
                return from.toString().trim();
            }
        }
        if (devCred != null && devCred.getCredentialsJson() != null) {
            Object from = devCred.getCredentialsJson().get("fromAddress");
            if (from != null && !from.toString().isBlank()) {
                return from.toString().trim();
            }
        }
        return appProperties.getMail().getFromAddress();
    }

    private String resolveFromName(NotificationMessage message, DeveloperChannelCredential devCred) {
        if (message.getChannelMetadata() != null) {
            Object name = message.getChannelMetadata().get("fromName");
            if (name == null) name = message.getChannelMetadata().get("from_name");
            if (name != null && !name.toString().isBlank()) {
                return name.toString().trim();
            }
        }
        if (devCred != null && devCred.getCredentialsJson() != null) {
            Object name = devCred.getCredentialsJson().get("fromName");
            if (name != null && !name.toString().isBlank()) {
                return name.toString().trim();
            }
        }
        return appProperties.getMail().getFromName();
    }

    private String resolveReplyTo(NotificationMessage message, DeveloperChannelCredential devCred) {
        if (message.getChannelMetadata() != null) {
            Object replyTo = message.getChannelMetadata().get("replyTo");
            if (replyTo == null) replyTo = message.getChannelMetadata().get("reply_to");
            if (replyTo != null && !replyTo.toString().isBlank()) {
                return replyTo.toString().trim();
            }
        }
        if (devCred != null && devCred.getCredentialsJson() != null) {
            Object replyTo = devCred.getCredentialsJson().get("replyTo");
            if (replyTo != null && !replyTo.toString().isBlank()) {
                return replyTo.toString().trim();
            }
        }
        return null;
    }
}
