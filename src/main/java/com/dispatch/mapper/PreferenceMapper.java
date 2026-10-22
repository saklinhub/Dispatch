package com.dispatch.mapper;

import com.dispatch.dto.response.PreferenceResponse;
import com.dispatch.entity.NotificationPreference;
import org.springframework.stereotype.Component;

/**
 * Maps NotificationPreference entity to PreferenceResponse DTO.
 */
@Component
public class PreferenceMapper {

    public PreferenceResponse toResponse(NotificationPreference pref) {
        return PreferenceResponse.builder()
                .id(pref.getId())
                .userId(pref.getUser().getId())
                .emailEnabled(pref.isEmailEnabled())
                .smsEnabled(pref.isSmsEnabled())
                .inAppEnabled(pref.isInAppEnabled())
                .preferredEmail(pref.getPreferredEmail())
                .preferredPhone(pref.getPreferredPhone())
                .quietHoursStart(pref.getQuietHoursStart())
                .quietHoursEnd(pref.getQuietHoursEnd())
                .timezone(pref.getTimezone())
                .createdAt(pref.getCreatedAt())
                .updatedAt(pref.getUpdatedAt())
                .build();
    }
}
