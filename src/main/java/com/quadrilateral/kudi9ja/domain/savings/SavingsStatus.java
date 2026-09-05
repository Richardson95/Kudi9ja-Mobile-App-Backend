package com.quadrilateral.kudi9ja.domain.savings;

/** Where a savings plan stands. */
public enum SavingsStatus {

    /** Running. Contributions may go in; the principal is locked. */
    ACTIVE("Locked"),

    /** Past its maturity date and waiting to be released to the wallet. */
    MATURED("Matured"),

    /** Released in full. A closed plan. */
    WITHDRAWN("Withdrawn"),

    /** Target only: ended early, principal returned, bonus forfeited. */
    BROKEN("Broken early"),

    /**
     * Fixed only: released early on death or permanent incapacity, in full and
     * without penalty, as the Terms provide.
     */
    RELEASED_ON_COMPASSIONATE_GROUNDS("Released early");

    private final String label;

    SavingsStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** A plan still on the customer's books. */
    public boolean isOpen() {
        return this == ACTIVE || this == MATURED;
    }
}
