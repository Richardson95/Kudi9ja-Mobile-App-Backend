package com.quadrilateral.kudi9ja.domain.wallet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One movement on a wallet. The ledger is <b>append-only</b>: no row is ever
 * deleted, and the only field that changes after a row is written is its status
 * — a pending withdrawal settling or reversing — and the description that goes
 * with it.
 *
 * <p>Every row carries the {@link #balanceAfter} it produced, so a statement
 * can be read without recomputing, and so a divergence between the ledger and
 * the wallet is visible at the row that caused it.
 */
@Entity
@Table(
        name = "wallet_transaction",
        indexes = {
                @Index(name = "ix_txn_wallet_date", columnList = "wallet_id, occurred_at"),
                @Index(name = "ix_txn_user_date", columnList = "user_id, occurred_at"),
                @Index(name = "ix_txn_reference", columnList = "reference"),
                @Index(name = "ix_txn_kind", columnList = "kind"),
                @Index(name = "ix_txn_sequence", columnList = "wallet_id, sequence", unique = true)
        })
@Getter
@Setter
@NoArgsConstructor
public class WalletTransaction {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "wallet_id", nullable = false, updatable = false)
    private UUID walletId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /** Gapless per wallet. Replaying in this order rebuilds the balance. */
    @Column(name = "sequence", nullable = false, updatable = false)
    private long sequence;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 32, updatable = false)
    private TxKind kind;

    /** Always positive. The {@link TxKind} says which way it moved. */
    @Column(name = "amount", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;

    /** What the customer reads on their statement. */
    @Column(name = "description", nullable = false, length = 300)
    private String description;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "balance_after", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal balanceAfter;

    @Column(name = "reference", nullable = false, length = 64, updatable = false)
    private String reference;

    /** Who the money went to or came from, as the customer would name them. */
    @Column(name = "counterparty", length = 160)
    private String counterparty;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private TxStatus status = TxStatus.SUCCESSFUL;

    /**
     * The savings plan, loan, circle, pay-in or withdrawal this row belongs to,
     * so a record can show its own ledger legs without a text search.
     */
    @Column(name = "related_type", length = 32, updatable = false)
    private String relatedType;

    @Column(name = "related_id", updatable = false)
    private UUID relatedId;

    public boolean isCredit() {
        return kind.isCredit();
    }

    public boolean isPending() {
        return status == TxStatus.PENDING;
    }

    /**
     * Every row moved the balance, reversed ones included.
     *
     * <p>A declined withdrawal was already debited when it was requested — that
     * is the point of debiting at request rather than at approval — so the
     * money really did leave. Marking the row reversed records what became of
     * the request; a separate compensating credit puts the money back. Both
     * rows stand, and replaying the ledger in sequence rebuilds the balance
     * exactly.
     */
    public boolean movedTheBalance() {
        return true;
    }

    /** Signed against the balance: positive for a credit, negative for a debit. */
    public BigDecimal signedAmount() {
        return isCredit() ? amount : amount.negate();
    }
}
