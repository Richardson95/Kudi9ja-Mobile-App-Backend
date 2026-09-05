package com.quadrilateral.kudi9ja.domain.wallet;

import com.quadrilateral.kudi9ja.common.util.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A balance and an append-only ledger.
 *
 * <p>The balance here is the running total of that ledger and <b>must be
 * reconstructible from it</b>. Any divergence is a defect, and
 * {@code LedgerService} carries a reconciliation that says so.
 *
 * <p>This is <b>not a bank account</b> and is <b>not NDIC-insured</b>. The
 * Terms say so in those words; nothing here may imply otherwise.
 *
 * <p>The row is versioned and taken under a pessimistic write lock for the
 * duration of any movement, so two concurrent debits cannot both read the same
 * balance and both succeed.
 */
@Entity
@Table(
        name = "wallet",
        indexes = @Index(name = "ix_wallet_user", columnList = "user_id", unique = true))
@Getter
@Setter
@NoArgsConstructor
public class Wallet {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /**
     * Never negative. Every debit checks first, inside the same transaction
     * that writes the ledger row.
     */
    @Column(name = "balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal balance = Money.zero();

    /** Where the next ledger row sits. Gapless, so the ledger can be replayed. */
    @Column(name = "next_sequence", nullable = false)
    private long nextSequence = 1L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    public static Wallet forUser(UUID userId) {
        Wallet wallet = new Wallet();
        wallet.id = UUID.randomUUID();
        wallet.userId = userId;
        wallet.balance = Money.zero();
        return wallet;
    }

    public boolean canAfford(BigDecimal amount) {
        return Money.gte(balance, amount);
    }

    long takeSequence() {
        return nextSequence++;
    }
}
