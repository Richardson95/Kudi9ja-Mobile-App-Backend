package com.quadrilateral.kudi9ja.domain.payin;

/** Where a claimed bank transfer sits in the confirmation queue. */
public enum DepositStatus {

    /** Claimed. Nothing has been credited and the balance is unchanged. */
    PENDING("Awaiting confirmation"),

    /** Matched against the bank statement by an admin. Money moved. */
    CONFIRMED("Confirmed"),

    /** Not found on the statement, or the receipt did not match. */
    REJECTED("Rejected");

    private final String label;

    DepositStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
