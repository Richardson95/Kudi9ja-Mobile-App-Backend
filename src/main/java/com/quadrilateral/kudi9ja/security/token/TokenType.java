package com.quadrilateral.kudi9ja.security.token;

/**
 * What a token is good for. The type is a claim and is checked on use, so a
 * refresh token cannot be presented as an access token or the other way round.
 */
public enum TokenType {

    /** Short-lived, sent on every request. */
    ACCESS,

    /** Long-lived, exchanged for a new access token and rotated on use. */
    REFRESH,

    /**
     * Issued mid-signup, after the email OTP and before the account exists.
     * Carries the draft, not an account, and buys nothing else.
     */
    SIGNUP
}
