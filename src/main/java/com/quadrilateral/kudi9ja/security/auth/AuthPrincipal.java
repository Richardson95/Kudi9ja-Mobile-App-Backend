package com.quadrilateral.kudi9ja.security.auth;

import java.util.UUID;

/**
 * Who is making this request, as established from a verified token and
 * re-checked against the database.
 *
 * <p>The admin flag here is not the token's claim. It is the answer the
 * database gave to "does this email currently hold active panel access", asked
 * on this request. Access is re-checked on every request precisely because a
 * client-held claim is trivially forged and a grant can be withdrawn between
 * one request and the next.
 */
public record AuthPrincipal(
        UUID userId,
        UUID sessionId,
        String email,
        String fullName,
        String customerRef,
        boolean admin) {

    public String describe() {
        return fullName + " (" + email + ")";
    }
}
