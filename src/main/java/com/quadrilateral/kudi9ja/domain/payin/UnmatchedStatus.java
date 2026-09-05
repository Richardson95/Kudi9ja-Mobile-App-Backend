package com.quadrilateral.kudi9ja.domain.payin;

/** Where an unmatched credit into the collection account stands. */
public enum UnmatchedStatus {

    /** Held while we try to work out whose it is. */
    HELD("Held"),

    /** Tied to a customer and credited. */
    MATCHED("Matched to a customer"),

    /** Sent back to the account it came from, as the Terms require. */
    RETURNED("Returned to source"),

    /** Could not be returned either. Escalated to the finance team. */
    UNRETURNABLE("Could not be returned");

    private final String label;

    UnmatchedStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
