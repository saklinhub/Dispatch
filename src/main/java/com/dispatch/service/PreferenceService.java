package com.dispatch.service;

import com.dispatch.dto.request.UpdatePreferenceRequest;
import com.dispatch.dto.response.PreferenceResponse;
import com.dispatch.entity.NotificationPreference;
import com.dispatch.entity.enums.NotificationType;

import java.util.UUID;

/**
 * Service managing user notification preferences, channel opt-ins,
 * destination overrides, and quiet hours configuration.
 */
public interface PreferenceService {

    /**
     * Retrieves preferences for a user, creating default preferences if none exist.
     */
    PreferenceResponse getPreferences(UUID userId);

    /**
     * Updates notification preferences and quiet hours for a user.
     */
    PreferenceResponse updatePreferences(UUID userId, UpdatePreferenceRequest request);

    /**
     * Toggles a specific delivery channel on or off.
     */
    PreferenceResponse toggleChannel(UUID userId, NotificationType channel, boolean enabled);

    /**
     * Retrieves the internal NotificationPreference entity or creates a default one.
     */
    NotificationPreference getOrCreateDefault(UUID userId);
}
