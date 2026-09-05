package com.quadrilateral.kudi9ja.domain.payin;

/** What a customer says their bank transfer was for. */
public enum DepositPurpose {

    WALLET("Wallet funding"),

    /**
     * Confirming one of these credits the wallet <b>and</b> immediately applies
     * the amount to the named loan, so both legs appear in the ledger.
     */
    LOAN_REPAYMENT("Loan repayment");

    private final String label;

    DepositPurpose(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
