package com.dispatch.resilience;

import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Classifies provider error codes and failure messages into TRANSIENT or PERMANENT categories.
 */
@Component
public class ErrorClassifier {

    private static final Set<String> PERMANENT_ERROR_CODES = Set.of(
            // Twilio permanent errors
            "TWILIO_21211", // Invalid 'To' Phone Number
            "TWILIO_21614", // 'To' number is not a valid mobile number
            "TWILIO_21610", // Message cannot be sent to unsubscribed recipient
            "TWILIO_21408", // Permission to send SMS not enabled for region
            "TWILIO_20003", // Authentication Error / Account Suspended

            // SMTP 5xx permanent bounce errors
            "SMTP_550", // Mailbox unavailable / not found
            "SMTP_551", // User not local
            "SMTP_552", // Exceeded storage allocation
            "SMTP_553", // Mailbox name not allowed
            "SMTP_554", // Transaction failed / permanent rejection
            "SMTP_501", // Syntax error in parameters

            // Internal permanent errors
            "USER_NOT_FOUND",
            "CANCELLED",
            "INVALID_ADDRESS",
            "PAYLOAD_INVALID"
    );

    private static final Set<String> TRANSIENT_ERROR_CODES = Set.of(
            // Twilio transient errors
            "TWILIO_20429", // Too Many Requests / Rate limit exceeded
            "TWILIO_500",   // Twilio internal server error
            "TWILIO_503",   // Twilio service unavailable
            "TWILIO_11200", // HTTP retrieval failure

            // SMTP 4xx temporary failure errors
            "SMTP_421", // Service not available, closing transmission channel
            "SMTP_450", // Mailbox busy
            "SMTP_451", // Local error in processing
            "SMTP_452", // Insufficient system storage

            // Internal transient errors
            "TIMEOUT",
            "NETWORK_ERROR",
            "CONNECTION_FAILED",
            "RATE_LIMIT"
    );

    /**
     * Classifies an error code and optional message into ErrorType.
     */
    public ErrorType classify(String errorCode, String errorMessage) {
        if (errorCode != null) {
            String upperCode = errorCode.trim().toUpperCase();

            if (PERMANENT_ERROR_CODES.contains(upperCode)) {
                return ErrorType.PERMANENT;
            }

            if (TRANSIENT_ERROR_CODES.contains(upperCode)) {
                return ErrorType.TRANSIENT;
            }

            if (upperCode.startsWith("SMTP_5")) {
                return ErrorType.PERMANENT;
            }
            if (upperCode.startsWith("SMTP_4")) {
                return ErrorType.TRANSIENT;
            }
        }

        if (errorMessage != null) {
            String lowerMsg = errorMessage.toLowerCase();
            if (lowerMsg.contains("invalid number") || lowerMsg.contains("not found") || lowerMsg.contains("unsubscribed")) {
                return ErrorType.PERMANENT;
            }
            if (lowerMsg.contains("timeout") || lowerMsg.contains("connection refused") || lowerMsg.contains("rate limit") || lowerMsg.contains("busy")) {
                return ErrorType.TRANSIENT;
            }
        }

        // Default to TRANSIENT so temporary failures get retried
        return ErrorType.TRANSIENT;
    }

    public boolean isTransient(String errorCode, String errorMessage) {
        return classify(errorCode, errorMessage) == ErrorType.TRANSIENT;
    }

    public boolean isPermanent(String errorCode, String errorMessage) {
        return classify(errorCode, errorMessage) == ErrorType.PERMANENT;
    }
}
