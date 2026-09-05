package com.quadrilateral.kudi9ja.common.error;

import java.time.Instant;
import java.util.Map;

/**
 * The body of every refused request. One shape, so the client has one place
 * to read an error from.
 *
 * @param code    the machine-readable reason, from {@link ErrorCode}
 * @param message what to show the customer
 * @param details field errors, limits, or whatever the code needs to be acted on
 * @param path    the request that was refused
 * @param at      when it was refused
 */
public record ApiError(
        String code,
        String message,
        Map<String, Object> details,
        String path,
        Instant at) {

    public static ApiError of(ErrorCode code, String message, Map<String, Object> details, String path) {
        return new ApiError(code.name(), message, details == null ? Map.of() : details, path, Instant.now());
    }
}
