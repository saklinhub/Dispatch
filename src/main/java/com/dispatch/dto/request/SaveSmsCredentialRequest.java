package com.dispatch.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveSmsCredentialRequest {

    @Builder.Default
    private String provider = "TWILIO";

    @NotBlank(message = "Account SID is required")
    private String accountSid;

    @NotBlank(message = "Auth Token is required")
    private String authToken;

    @NotBlank(message = "From phone number is required")
    private String fromNumber;

    private String messagingServiceSid;
}
