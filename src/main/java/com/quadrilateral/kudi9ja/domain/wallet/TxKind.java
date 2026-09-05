package com.quadrilateral.kudi9ja.domain.wallet;

/**
 * What a ledger row is. Four kinds increase the balance; everything else
 * reduces it.
 */
public enum TxKind {

    DEPOSIT("Wallet funding", true),
    WITHDRAWAL("Withdrawal", false),
    SAVINGS_LOCK("Savings lock", false),
    INTEREST_PAYOUT("Interest payout", true),
    SAVINGS_RELEASE("Savings release", true),
    LOAN_DISBURSEMENT("Loan disbursed", true),
    LOAN_REPAYMENT("Loan repayment", false),
    FEE("Service fee", false);

    private final String label;
    private final boolean credit;

    TxKind(String label, boolean credit) {
        this.label = label;
        this.credit = credit;
    }

    public String label() {
        return label;
    }

    /** Whether this row increases the balance. */
    public boolean isCredit() {
        return credit;
    }
}
