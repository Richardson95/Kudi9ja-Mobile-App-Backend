package com.quadrilateral.kudi9ja.domain.kyc;

/** What a one-time code is being asked for. A code is good for one of these only. */
public enum OtpPurpose {

    /** Step two of signup: proving the email address exists and is theirs. */
    SIGNUP_EMAIL,

    /** Resetting a forgotten password. */
    PASSWORD_RESET,

    /** Re-verifying an account that went dormant. */
    REACTIVATION,

    /** Confirming a change to the payout account, which is where money leaves. */
    PAYOUT_CHANGE,

    /** Confirming a request to close the account. */
    ACCOUNT_CLOSURE
}
