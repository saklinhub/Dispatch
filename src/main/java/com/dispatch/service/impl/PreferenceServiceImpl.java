package com.dispatch.service.impl;

import com.dispatch.dto.request.UpdatePreferenceRequest;
import com.dispatch.dto.response.PreferenceResponse;
import com.dispatch.entity.NotificationPreference;
import com.dispatch.entity.User;
import com.dispatch.entity.enums.NotificationType;
import com.dispatch.exception.ResourceNotFoundException;
import com.dispatch.mapper.PreferenceMapper;
import com.dispatch.preference.QuietHoursEvaluator;
import com.dispatch.repository.NotificationPreferenceRepository;
import com.dispatch.repository.UserRepository;
import com.dispatch.service.PreferenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PreferenceServiceImpl implements PreferenceService {

    private final NotificationPreferenceRepository preferenceRepository;
    private final UserRepository userRepository;
    private final QuietHoursEvaluator quietHoursEvaluator;
    private final PreferenceMapper mapper;

    @Override
    @Transactional
    public PreferenceResponse getPreferences(UUID userId) {
        NotificationPreference preference = getOrCreateDefault(userId);
        return mapper.toResponse(preference);
    }

    @Override
    @Transactional
    public PreferenceResponse updatePreferences(UUID userId, UpdatePreferenceRequest req) {
        if (req.getTimezone() != null && !req.getTimezone().isBlank()) {
            if (!quietHoursEvaluator.isValidTimezone(req.getTimezone())) {
                throw new IllegalArgumentException("Invalid IANA timezone: " + req.getTimezone());
            }
        }

        NotificationPreference preference = getOrCreateDefault(userId);

        if (req.getEmailEnabled() != null) {
            preference.setEmailEnabled(req.getEmailEnabled());
        }
        if (req.getSmsEnabled() != null) {
            preference.setSmsEnabled(req.getSmsEnabled());
        }
        if (req.getInAppEnabled() != null) {
            preference.setInAppEnabled(req.getInAppEnabled());
        }

        if (req.getPreferredEmail() != null) {
            preference.setPreferredEmail(req.getPreferredEmail().trim().isEmpty() ? null : req.getPreferredEmail().trim());
        }
        if (req.getPreferredPhone() != null) {
            preference.setPreferredPhone(req.getPreferredPhone().trim().isEmpty() ? null : req.getPreferredPhone().trim());
        }

        if (req.getQuietHoursStart() != null) {
            preference.setQuietHoursStart(req.getQuietHoursStart());
        }
        if (req.getQuietHoursEnd() != null) {
            preference.setQuietHoursEnd(req.getQuietHoursEnd());
        }
        if (req.getTimezone() != null && !req.getTimezone().isBlank()) {
            preference.setTimezone(req.getTimezone().trim());
        }

        preference = preferenceRepository.save(preference);
        log.info("Updated notification preferences for user {}", userId);
        return mapper.toResponse(preference);
    }

    @Override
    @Transactional
    public PreferenceResponse toggleChannel(UUID userId, NotificationType channel, boolean enabled) {
        NotificationPreference preference = getOrCreateDefault(userId);

        switch (channel) {
            case EMAIL -> preference.setEmailEnabled(enabled);
            case SMS -> preference.setSmsEnabled(enabled);
            case IN_APP -> preference.setInAppEnabled(enabled);
        }

        preference = preferenceRepository.save(preference);
        log.info("Toggled channel {} to {} for user {}", channel, enabled, userId);
        return mapper.toResponse(preference);
    }

    @Override
    @Transactional
    public NotificationPreference getOrCreateDefault(UUID userId) {
        return preferenceRepository.findByUserId(userId).orElseGet(() -> {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> ResourceNotFoundException.user(userId.toString()));

            NotificationPreference defaultPref = NotificationPreference.builder()
                    .user(user)
                    .emailEnabled(true)
                    .smsEnabled(true)
                    .inAppEnabled(true)
                    .timezone("UTC")
                    .build();

            return preferenceRepository.save(defaultPref);
        });
    }
}
