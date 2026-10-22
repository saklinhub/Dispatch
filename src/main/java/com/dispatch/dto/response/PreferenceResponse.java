package com.dispatch.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Representation of a user's notification preferences and quiet hours.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreferenceResponse {

    private UUID id;
    private UUID userId;
    private boolean emailEnabled;
    private boolean smsEnabled;
    private boolean inAppEnabled;
    private String preferredEmail;
    private String preferredPhone;
    private LocalTime quietHoursStart;
    private LocalTime quietHoursEnd;
    private String timezone;
    private Instant createdAt;
    private Instant updatedAt;
}
