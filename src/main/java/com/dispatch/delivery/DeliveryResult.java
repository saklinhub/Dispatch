package com.dispatch.delivery;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result returned by a DeliveryStrategy after attempting notification dispatch.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryResult {

    private boolean success;
    private String providerResponse;
    private String errorCode;
    private String errorMessage;

    public static DeliveryResult success(String providerResponse) {
        return DeliveryResult.builder()
                .success(true)
                .providerResponse(providerResponse)
                .build();
    }

    public static DeliveryResult failure(String errorCode, String errorMessage) {
        return DeliveryResult.builder()
                .success(false)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .build();
    }

    public static DeliveryResult failure(String errorCode, String errorMessage, String providerResponse) {
        return DeliveryResult.builder()
                .success(false)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .providerResponse(providerResponse)
                .build();
    }
}
