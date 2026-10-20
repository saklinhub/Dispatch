package com.dispatch.delivery.strategy;

import com.dispatch.config.AppProperties;
import com.dispatch.delivery.DeliveryResult;
import com.dispatch.delivery.DeliveryStrategy;
import com.dispatch.dto.message.NotificationMessage;
import com.dispatch.entity.enums.NotificationType;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Strategy implementation for delivering notifications via SMTP Email.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailDeliveryStrategy implements DeliveryStrategy {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final AppProperties appProperties;

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
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();

        // If credentials are not configured or mail sender is absent, simulate delivery
        if (mailSender == null || mailUsername.isBlank() || mailPassword.isBlank()) {
            log.info("[SIMULATED EMAIL] To: {} | Subject: '{}' | Body: '{}'",
                    message.getRecipientAddress(), message.getSubject(), message.getBody());
            return DeliveryResult.success("Simulated email dispatched to " + message.getRecipientAddress());
        }

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, "UTF-8");

            String fromAddress = appProperties.getMail().getFromAddress();
            String fromName = appProperties.getMail().getFromName();

            if (fromName != null && !fromName.isBlank()) {
                helper.setFrom(fromAddress, fromName);
            } else {
                helper.setFrom(fromAddress);
            }

            helper.setTo(message.getRecipientAddress());
            helper.setSubject(message.getSubject() != null ? message.getSubject() : "Dispatch Notification");
            helper.setText(message.getBody(), false);

            mailSender.send(mimeMessage);
            log.info("Sent EMAIL notification {} to {}", message.getNotificationId(), message.getRecipientAddress());
            return DeliveryResult.success("SMTP 250 OK - message queued for " + message.getRecipientAddress());

        } catch (Exception ex) {
            log.error("Failed to send EMAIL notification {}: {}", message.getNotificationId(), ex.getMessage());
            return DeliveryResult.failure("SMTP_SEND_FAILED", ex.getMessage());
        }
    }
}
