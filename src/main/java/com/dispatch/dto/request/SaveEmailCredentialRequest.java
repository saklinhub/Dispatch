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
public class SaveEmailCredentialRequest {

    /**
     * Provider type, e.g. GMAIL, SENDGRID, RESEND, MAILTRAP, SMTP
     */
    @Builder.Default
    private String provider = "SMTP";

    /**
     * SMTP host, e.g. smtp.gmail.com. Automatically deduced if known provider.
     */
    private String host;

    /**
     * SMTP port, e.g. 587 (TLS) or 465 (SSL). Defaults to 587.
     */
    @Builder.Default
    private int port = 587;

    /**
     * SMTP username (e.g. Gmail address, SendGrid 'apikey', or SMTP login)
     */
    @NotBlank(message = "Username is required")
    private String username;

    /**
     * SMTP password / App Password / API Key
     */
    @NotBlank(message = "Password / API key is required")
    private String password;

    /**
     * Default From Address for this developer (e.g. alice@gmail.com)
     */
    @NotBlank(message = "From address is required")
    private String fromAddress;

    /**
     * Default From Name for this developer (e.g. Alice's Project)
     */
    private String fromName;

    /**
     * Optional default Reply-To address
     */
    private String replyTo;
}
