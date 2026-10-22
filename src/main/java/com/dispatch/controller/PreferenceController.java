package com.dispatch.controller;

import com.dispatch.dto.request.UpdatePreferenceRequest;
import com.dispatch.dto.response.PreferenceResponse;
import com.dispatch.entity.enums.NotificationType;
import com.dispatch.service.PreferenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for managing user notification preferences, channel opt-ins,
 * destination overrides, and timezone-aware quiet hours.
 */
@RestController
@RequestMapping("/api/v1/users/{userId}/preferences")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "User Preferences", description = "Manage per-user channel opt-ins, address overrides, and quiet hours")
public class PreferenceController {

    private final PreferenceService preferenceService;

    @GetMapping
    @Operation(
            summary = "Get user preferences",
            description = "Retrieves the notification preferences for a user, creating default preferences if none exist."
    )
    public ResponseEntity<PreferenceResponse> getPreferences(@PathVariable UUID userId) {
        return ResponseEntity.ok(preferenceService.getPreferences(userId));
    }

    @PutMapping
    @Operation(
            summary = "Update user preferences",
            description = "Updates channel opt-ins, preferred addresses, and timezone-aware quiet hours."
    )
    public ResponseEntity<PreferenceResponse> updatePreferences(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdatePreferenceRequest request) {
        return ResponseEntity.ok(preferenceService.updatePreferences(userId, request));
    }

    @PatchMapping("/channels/{channel}")
    @Operation(
            summary = "Toggle channel opt-in",
            description = "Quick toggle to enable or disable a specific delivery channel (EMAIL, SMS, IN_APP)."
    )
    public ResponseEntity<PreferenceResponse> toggleChannel(
            @PathVariable UUID userId,
            @PathVariable NotificationType channel,
            @Parameter(description = "Enable or disable channel") @RequestParam boolean enabled) {
        return ResponseEntity.ok(preferenceService.toggleChannel(userId, channel, enabled));
    }
}
