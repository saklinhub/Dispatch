package com.dispatch.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "User login credentials")
public class LoginRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    @Schema(description = "Registered email address", example = "alice@example.com")
    private String email;

    @NotBlank(message = "Password is required")
    @Schema(description = "Account password", example = "Password@123")
    private String password;
}
