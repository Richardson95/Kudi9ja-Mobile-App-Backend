package com.quadrilateral.kudi9ja.domain.wallet;

/**
 * Where a transaction stands.
 *
 * <p>Almost everything settles successful immediately. A withdrawal is written
 * pending — the wallet is debited at request, not at approval, so the money
 * cannot be spent twice while it is being reviewed — and becomes successful on
 * approval or reversed on decline.
 *
 * <p>A reversed transaction is never deleted. The ledger is append-only: the
 * refund is a state change on the same row plus a new credit row.
 */
public enum TxStatus {

    PENDING("Awaiting approval"),
    SUCCESSFUL("Successful"),
    REVERSED("Reversed");

    private final String label;

    TxStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
