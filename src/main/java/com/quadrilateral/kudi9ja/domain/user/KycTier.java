package com.quadrilateral.kudi9ja.domain.user;

/**
 * How far an account has been verified. The tier gates what the account may do.
 *
 * <p>The Flutter client assumes tier two for everyone. The server does not: an
 * account reaches tier one when its email is verified, and tier two only when
 * BVN and NIN have been checked against the issuing institutions and the name
 * and date of birth returned match what was typed.
 */
public enum KycTier {

    /** Signed up, email not yet verified. May do nothing with money. */
    TIER0("Unverified"),

    /** Email verified. May hold a wallet, pay in, save and transfer. */
    TIER1("Verified"),

    /** BVN and NIN confirmed. May withdraw and borrow. */
    TIER2("Fully Verified");

    private final String label;

    KycTier(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Whether this tier is at least as verified as {@code required}. */
    public boolean atLeast(KycTier required) {
        return ordinal() >= required.ordinal();
    }
}
