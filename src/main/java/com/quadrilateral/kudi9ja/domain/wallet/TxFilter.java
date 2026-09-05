package com.quadrilateral.kudi9ja.domain.wallet;

import java.util.EnumSet;
import java.util.Set;

/**
 * The filter set shared by the customer wallet and the admin customer record,
 * so both screens slice history the same way.
 */
public enum TxFilter {

    ALL("All", EnumSet.allOf(TxKind.class)),
    DEPOSITS("Deposits", EnumSet.of(TxKind.DEPOSIT)),
    WITHDRAWALS("Withdrawals", EnumSet.of(TxKind.WITHDRAWAL)),
    SAVINGS("Savings", EnumSet.of(TxKind.SAVINGS_LOCK, TxKind.INTEREST_PAYOUT, TxKind.SAVINGS_RELEASE)),
    LOANS("Loans", EnumSet.of(TxKind.LOAN_DISBURSEMENT, TxKind.LOAN_REPAYMENT)),
    FEES("Fees", EnumSet.of(TxKind.FEE));

    private final String label;
    private final Set<TxKind> kinds;

    TxFilter(String label, Set<TxKind> kinds) {
        this.label = label;
        this.kinds = kinds;
    }

    public String label() {
        return label;
    }

    public Set<TxKind> kinds() {
        return kinds;
    }

    public boolean matches(TxKind kind) {
        return kinds.contains(kind);
    }
}
