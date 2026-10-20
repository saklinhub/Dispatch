package com.dispatch.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "JWT authentication response")
public class AuthResponse {

    @Schema(description = "JWT access token")
    private String accessToken;

    @Schema(description = "JWT refresh token")
    private String refreshToken;

    @Builder.Default
    @Schema(description = "Token authorization scheme", example = "Bearer")
    private String tokenType = "Bearer";

    @Schema(description = "Access token time to live in milliseconds", example = "86400000")
    private long expiresIn;

    @Schema(description = "Authenticated user profile")
    private UserResponse user;
}
