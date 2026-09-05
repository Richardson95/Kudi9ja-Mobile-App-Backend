package com.quadrilateral.kudi9ja.common.error;

import java.util.Map;

/**
 * A refusal the customer is meant to read. The message explains the rule that
 * was broken; the code lets the client branch without parsing English.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final transient Map<String, Object> details;

    public ApiException(ErrorCode code, String message) {
        this(code, message, Map.of());
    }

    public ApiException(ErrorCode code, String message, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.details = details == null ? Map.of() : details;
    }

    public ErrorCode code() {
        return code;
    }

    public Map<String, Object> details() {
        return details;
    }

    // Shorthands for the refusals raised in more than one place ---------------

    public static ApiException notFound(String what) {
        return new ApiException(ErrorCode.NOT_FOUND, what + " was not found.");
    }

    public static ApiException forbidden(String message) {
        return new ApiException(ErrorCode.FORBIDDEN, message);
    }

    public static ApiException validation(String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, message);
    }

    public static ApiException insufficientFunds() {
        return new ApiException(
                ErrorCode.INSUFFICIENT_FUNDS,
                "Your wallet does not hold enough for this. Add money and try again.");
    }
}
