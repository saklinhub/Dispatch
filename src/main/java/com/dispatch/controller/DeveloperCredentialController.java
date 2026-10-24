package com.dispatch.controller;

import com.dispatch.dto.request.SaveEmailCredentialRequest;
import com.dispatch.dto.request.SaveSmsCredentialRequest;
import com.dispatch.dto.response.DeveloperCredentialResponse;
import com.dispatch.entity.enums.NotificationType;
import com.dispatch.service.DeveloperCredentialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for developers to manage their own custom email/SMS credentials
 * (Multi-Tenant Delivery).
 */
@RestController
@RequestMapping("/api/v1/users/{userId}/credentials")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Developer Credentials", description = "Manage custom multi-tenant delivery provider credentials (e.g. personal Gmail, SendGrid, Twilio)")
public class DeveloperCredentialController {

    private final DeveloperCredentialService credentialService;

    @GetMapping
    @Operation(
            summary = "List developer credentials",
            description = "Returns all configured delivery credentials (with sensitive keys masked) for the user."
    )
    public ResponseEntity<List<DeveloperCredentialResponse>> getCredentials(@PathVariable UUID userId) {
        return ResponseEntity.ok(credentialService.getCredentials(userId));
    }

    @PutMapping("/email")
    @Operation(
            summary = "Configure custom email credentials",
            description = "Saves or updates custom email SMTP credentials (e.g. Gmail App Password, SendGrid API key, Mailtrap) for this developer."
    )
    public ResponseEntity<DeveloperCredentialResponse> saveEmailCredentials(
            @PathVariable UUID userId,
            @Valid @RequestBody SaveEmailCredentialRequest request) {
        return ResponseEntity.ok(credentialService.saveEmailCredentials(userId, request));
    }

    @PutMapping("/sms")
    @Operation(
            summary = "Configure custom SMS credentials",
            description = "Saves or updates custom Twilio credentials for this developer."
    )
    public ResponseEntity<DeveloperCredentialResponse> saveSmsCredentials(
            @PathVariable UUID userId,
            @Valid @RequestBody SaveSmsCredentialRequest request) {
        return ResponseEntity.ok(credentialService.saveSmsCredentials(userId, request));
    }

    @DeleteMapping("/{channel}")
    @Operation(
            summary = "Delete custom channel credentials",
            description = "Removes custom credentials for the channel, reverting delivery back to system default / simulation mode."
    )
    public ResponseEntity<Void> deleteCredentials(
            @PathVariable UUID userId,
            @PathVariable NotificationType channel) {
        credentialService.deleteCredential(userId, channel);
        return ResponseEntity.noContent().build();
    }
}
