package com.quadrilateral.kudi9ja.domain.payout;

/** Where a withdrawal request sits in the approval queue. */
public enum WithdrawalStatus {

    /** Requested. The wallet is already debited and a pending row is written. */
    PENDING("Awaiting approval"),

    /** Released to the customer's own bank account. */
    APPROVED("Approved"),

    /** Refused. The pending transaction is reversed and refunded in full. */
    DECLINED("Declined");

    private final String label;

    WithdrawalStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
