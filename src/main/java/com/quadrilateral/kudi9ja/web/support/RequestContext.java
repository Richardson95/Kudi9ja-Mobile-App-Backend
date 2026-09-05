package com.quadrilateral.kudi9ja.web.support;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The two things about a request that are worth recording alongside what it
 * did: which device made it, and where from.
 *
 * <p>Both are evidence rather than decoration. The Terms rely on the Evidence
 * Act 2011 in saying that our records of what a customer accepted, and when,
 * are admissible — a legal acceptance carrying a device and an address is worth
 * considerably more than a bare timestamp. The same pair on a sign-in is what
 * lets a customer look at their own security screen and recognise a session
 * that is not theirs.
 */
public final class RequestContext {

    /**
     * The header the app sets to describe itself, for example
     * {@code "Pixel 8 · Android 15 · Kudi9ja 1.4.2"}.
     */
    private static final String DEVICE_HEADER = "X-Device";

    /**
     * Set by a reverse proxy, and therefore <b>not to be trusted for anything
     * that decides an outcome</b>. A client can send whatever it likes in it.
     * It is recorded because it is usually right and useful when reading a log
     * back; nothing is authorised on the strength of it.
     */
    private static final String FORWARDED_FOR = "X-Forwarded-For";

    private RequestContext() {
    }

    /** A short description of the calling device, or a placeholder. */
    public static String device(HttpServletRequest request) {
        if (request == null) {
            return "Unknown device";
        }
        String declared = request.getHeader(DEVICE_HEADER);
        if (declared != null && !declared.isBlank()) {
            return truncate(declared.trim(), 200);
        }
        String agent = request.getHeader("User-Agent");
        return agent == null || agent.isBlank() ? "Unknown device" : truncate(agent.trim(), 200);
    }

    /** The caller's address as best it can be told. */
    public static String ipAddress(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader(FORWARDED_FOR);
        if (forwarded != null && !forwarded.isBlank()) {
            // The header is a chain: the client is the first entry, each proxy
            // appends itself.
            return truncate(forwarded.split(",")[0].trim(), 45);
        }
        return truncate(request.getRemoteAddr(), 45);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
