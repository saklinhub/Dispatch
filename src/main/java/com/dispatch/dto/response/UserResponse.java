package com.dispatch.dto.response;

import com.dispatch.entity.enums.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "User details representation")
public class UserResponse {

    @Schema(description = "Unique user ID")
    private UUID id;

    @Schema(description = "Registered email address")
    private String email;

    @Schema(description = "Full name")
    private String fullName;

    @Schema(description = "Phone number in E.164 format")
    private String phoneNumber;

    @Schema(description = "Assigned user role")
    private UserRole role;

    @Schema(description = "Account creation timestamp")
    private Instant createdAt;
}
