package com.quadrilateral.kudi9ja.domain.thrift;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * One member paying into one round.
 *
 * <p>Recorded per member and per round rather than as a list of round numbers
 * on the member, so the circle can answer the question everybody in an ajo
 * actually asks: <i>who has paid this round?</i> The client can only ever
 * answer it for the one person holding the phone.
 */
@Entity
@Table(
        name = "thrift_contribution",
        indexes = {
                @Index(name = "ix_contribution_circle_round", columnList = "circle_id, round"),
                @Index(name = "ix_contribution_unique", columnList = "circle_id, user_id, round",
                        unique = true)
        })
@Getter
@Setter
@NoArgsConstructor
public class ThriftContribution {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "circle_id", nullable = false, updatable = false)
    private UUID circleId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "round", nullable = false, updatable = false)
    private int round;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "paid_at", nullable = false, updatable = false)
    private Instant paidAt = Instant.now();

    /** The ledger row this contribution produced. */
    @Column(name = "transaction_id", updatable = false)
    private UUID transactionId;

    public static ThriftContribution of(
            UUID circleId, UUID userId, int round, BigDecimal amount, UUID transactionId) {
        ThriftContribution contribution = new ThriftContribution();
        contribution.id = UUID.randomUUID();
        contribution.circleId = circleId;
        contribution.userId = userId;
        contribution.round = round;
        contribution.amount = amount;
        contribution.transactionId = transactionId;
        return contribution;
    }
}
