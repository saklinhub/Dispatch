package com.dispatch.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Payload to register a new user account")
public class RegisterRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    @Schema(description = "Unique email address", example = "alice@example.com")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters long")
    @Schema(description = "Raw password (minimum 8 characters)", example = "Password@123")
    private String password;

    @NotBlank(message = "Full name is required")
    @Schema(description = "User's full name", example = "Alice Smith")
    private String fullName;

    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Phone number must be in E.164 format (e.g. +15555550100)")
    @Schema(description = "Optional phone number in E.164 format", example = "+15555550100")
    private String phoneNumber;
}
