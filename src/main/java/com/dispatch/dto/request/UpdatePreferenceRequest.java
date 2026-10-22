package com.dispatch.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

/**
 * Request body for updating user notification preferences and quiet hours.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePreferenceRequest {

    private Boolean emailEnabled;
    private Boolean smsEnabled;
    private Boolean inAppEnabled;

    @Email(message = "Preferred email must be a valid email address")
    @Size(max = 255, message = "Preferred email cannot exceed 255 characters")
    private String preferredEmail;

    @Pattern(regexp = "^$|^\\+[1-9]\\d{1,14}$", message = "Preferred phone must follow E.164 format (e.g. +15555550100)")
    @Size(max = 20, message = "Preferred phone cannot exceed 20 characters")
    private String preferredPhone;

    private LocalTime quietHoursStart;
    private LocalTime quietHoursEnd;

    @Size(max = 100, message = "Timezone identifier cannot exceed 100 characters")
    private String timezone;
}
