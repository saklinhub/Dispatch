package com.dispatch.service.impl;

import com.dispatch.dto.request.SaveEmailCredentialRequest;
import com.dispatch.dto.request.SaveSmsCredentialRequest;
import com.dispatch.dto.response.DeveloperCredentialResponse;
import com.dispatch.entity.DeveloperChannelCredential;
import com.dispatch.entity.User;
import com.dispatch.entity.enums.NotificationType;
import com.dispatch.exception.ResourceNotFoundException;
import com.dispatch.repository.DeveloperChannelCredentialRepository;
import com.dispatch.repository.UserRepository;
import com.dispatch.service.DeveloperCredentialService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeveloperCredentialServiceImpl implements DeveloperCredentialService {

    private final DeveloperChannelCredentialRepository credentialRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public DeveloperCredentialResponse saveEmailCredentials(UUID userId, SaveEmailCredentialRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.user(userId.toString()));

        String provider = request.getProvider() != null ? request.getProvider().toUpperCase().trim() : "SMTP";
        String host = resolveHost(provider, request.getHost());
        int port = resolvePort(provider, request.getPort());

        Map<String, Object> credentials = new HashMap<>();
        credentials.put("provider", provider);
        credentials.put("host", host);
        credentials.put("port", port);
        credentials.put("username", request.getUsername().trim());
        credentials.put("password", request.getPassword().trim());
        credentials.put("fromAddress", request.getFromAddress().trim());
        if (request.getFromName() != null && !request.getFromName().isBlank()) {
            credentials.put("fromName", request.getFromName().trim());
        }
        if (request.getReplyTo() != null && !request.getReplyTo().isBlank()) {
            credentials.put("replyTo", request.getReplyTo().trim());
        }

        DeveloperChannelCredential credential = credentialRepository
                .findByUserIdAndChannel(userId, NotificationType.EMAIL)
                .orElseGet(() -> DeveloperChannelCredential.builder()
                        .user(user)
                        .channel(NotificationType.EMAIL)
                        .build());

        credential.setProvider(provider);
        credential.setCredentialsJson(credentials);
        credential.setActive(true);

        credential = credentialRepository.save(credential);
        log.info("Saved custom EMAIL credentials for user {} with provider {}", userId, provider);

        return toResponse(credential);
    }

    @Override
    @Transactional
    public DeveloperCredentialResponse saveSmsCredentials(UUID userId, SaveSmsCredentialRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.user(userId.toString()));

        String provider = request.getProvider() != null ? request.getProvider().toUpperCase().trim() : "TWILIO";

        Map<String, Object> credentials = new HashMap<>();
        credentials.put("provider", provider);
        credentials.put("accountSid", request.getAccountSid().trim());
        credentials.put("authToken", request.getAuthToken().trim());
        credentials.put("fromNumber", request.getFromNumber().trim());
        if (request.getMessagingServiceSid() != null && !request.getMessagingServiceSid().isBlank()) {
            credentials.put("messagingServiceSid", request.getMessagingServiceSid().trim());
        }

        DeveloperChannelCredential credential = credentialRepository
                .findByUserIdAndChannel(userId, NotificationType.SMS)
                .orElseGet(() -> DeveloperChannelCredential.builder()
                        .user(user)
                        .channel(NotificationType.SMS)
                        .build());

        credential.setProvider(provider);
        credential.setCredentialsJson(credentials);
        credential.setActive(true);

        credential = credentialRepository.save(credential);
        log.info("Saved custom SMS credentials for user {} with provider {}", userId, provider);

        return toResponse(credential);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeveloperCredentialResponse> getCredentials(UUID userId) {
        return credentialRepository.findByUserId(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public void deleteCredential(UUID userId, NotificationType channel) {
        credentialRepository.deleteByUserIdAndChannel(userId, channel);
        log.info("Deleted custom {} credentials for user {}", channel, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DeveloperChannelCredential> findActiveCredential(UUID userId, NotificationType channel) {
        if (userId == null) {
            return Optional.empty();
        }
        return credentialRepository.findByUserIdAndChannelAndIsActiveTrue(userId, channel);
    }

    private String resolveHost(String provider, String host) {
        if (host != null && !host.isBlank()) {
            return host.trim();
        }
        return switch (provider) {
            case "GMAIL" -> "smtp.gmail.com";
            case "SENDGRID" -> "smtp.sendgrid.net";
            case "RESEND" -> "smtp.resend.com";
            case "MAILTRAP" -> "sandbox.smtp.mailtrap.io";
            case "AWS_SES" -> "email-smtp.us-east-1.amazonaws.com";
            default -> "smtp.gmail.com";
        };
    }

    private int resolvePort(String provider, int port) {
        if (port > 0) {
            return port;
        }
        return "MAILTRAP".equalsIgnoreCase(provider) ? 2525 : 587;
    }

    private DeveloperCredentialResponse toResponse(DeveloperChannelCredential entity) {
        Map<String, Object> json = entity.getCredentialsJson();
        String from = null;
        String fromName = null;
        String maskedUser = null;
        String host = null;
        Integer port = null;

        if (entity.getChannel() == NotificationType.EMAIL) {
            from = (String) json.get("fromAddress");
            fromName = (String) json.get("fromName");
            host = (String) json.get("host");
            if (json.get("port") instanceof Number n) {
                port = n.intValue();
            }
            maskedUser = maskString((String) json.get("username"));
        } else if (entity.getChannel() == NotificationType.SMS) {
            from = (String) json.get("fromNumber");
            maskedUser = maskString((String) json.get("accountSid"));
        }

        return DeveloperCredentialResponse.builder()
                .id(entity.getId())
                .userId(entity.getUser().getId())
                .channel(entity.getChannel())
                .provider(entity.getProvider())
                .fromAddressOrNumber(from)
                .fromName(fromName)
                .maskedUsernameOrSid(maskedUser)
                .host(host)
                .port(port)
                .active(entity.isActive())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private String maskString(String input) {
        if (input == null || input.isBlank()) return "***";
        int len = input.length();
        if (len <= 4) return "****";
        return input.substring(0, 2) + "****" + input.substring(len - 2);
    }
}
