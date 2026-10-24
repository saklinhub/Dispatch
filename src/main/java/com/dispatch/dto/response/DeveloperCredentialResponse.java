package com.dispatch.dto.response;

import com.dispatch.entity.enums.NotificationType;
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
public class DeveloperCredentialResponse {

    private UUID id;
    private UUID userId;
    private NotificationType channel;
    private String provider;
    private String fromAddressOrNumber;
    private String fromName;
    private String maskedUsernameOrSid;
    private String host;
    private Integer port;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
}
