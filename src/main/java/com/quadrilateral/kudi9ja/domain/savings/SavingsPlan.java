package com.quadrilateral.kudi9ja.domain.savings;

import com.quadrilateral.kudi9ja.common.util.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * A savings plan, of one of the two kinds.
 *
 * <p><b>The plan carries its own terms.</b> {@code interestPaid} on a Fixed
 * plan and {@code bonusRate} on a Target plan are written when the plan opens
 * and are never recomputed. A later change to the platform rate has no way to
 * reach a running plan, because nothing running ever reads the platform rate
 * again.
 *
 * <p>The two kinds differ in exactly two ways, and both follow from the same
 * decision about when the return is paid:
 *
 * <ul>
 *   <li><b>Fixed</b> pays its whole return into the wallet the moment the plan
 *       is created, and therefore <b>cannot be broken</b> — the return was paid
 *       upfront precisely because the principal stays put.
 *   <li><b>Target</b> pays a bonus on the final day, and can be broken at any
 *       time: every naira saved comes back in full and the bonus is forfeited.
 * </ul>
 */
@Entity
@Table(
        name = "savings_plan",
        indexes = {
                @Index(name = "ix_plan_user", columnList = "user_id, start_date"),
                @Index(name = "ix_plan_status", columnList = "status"),
                @Index(name = "ix_plan_maturity", columnList = "status, maturity_date"),
                @Index(name = "ix_plan_auto", columnList = "auto_enabled, next_auto_run")
        })
@Getter
@Setter
@NoArgsConstructor
public class SavingsPlan {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "title", nullable = false, length = 120)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16, updatable = false)
    private SavingsType type;

    /** Everything locked in, including every top-up and auto-save so far. */
    @Column(name = "principal", nullable = false, precision = 19, scale = 2)
    private BigDecimal principal = Money.zero();

    /** How long the principal is locked for, in days. */
    @Column(name = "lock_days", nullable = false)
    private int lockDays;

    /**
     * Every naira of return credited on this plan so far. On a Fixed plan the
     * opening interest and each top-up's interest; on a Target plan the
     * completion bonus once it is paid.
     */
    @Column(name = "interest_paid", nullable = false, precision = 19, scale = 2)
    private BigDecimal interestPaid = Money.zero();

    /**
     * The annual rate this plan was priced at. Frozen at creation and kept so a
     * top-up is priced on the plan's own terms, not on today's.
     */
    @Column(name = "annual_rate", precision = 12, scale = 6)
    private BigDecimal annualRate;

    @Column(name = "start_date", nullable = false, updatable = false)
    private Instant startDate;

    @Column(name = "maturity_date", nullable = false)
    private Instant maturityDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private SavingsStatus status = SavingsStatus.ACTIVE;

    @Column(name = "emoji", length = 16)
    private String emoji;

    // Target only ------------------------------------------------------------

    /** What the customer is working towards. */
    @Column(name = "target_amount", precision = 19, scale = 2)
    private BigDecimal targetAmount;

    /** The term in months, which is what the bonus tier was chosen from. */
    @Column(name = "target_months")
    private Integer targetMonths;

    @Enumerated(EnumType.STRING)
    @Column(name = "auto_frequency", length = 16)
    private AutoFrequency autoFrequency;

    @Column(name = "auto_amount", precision = 19, scale = 2)
    private BigDecimal autoAmount;

    @Column(name = "next_auto_run")
    private Instant nextAutoRun;

    @Column(name = "auto_enabled", nullable = false)
    private boolean autoEnabled = false;

    /** The bonus rate fixed at creation. Zero on a Fixed plan. */
    @Column(name = "bonus_rate", nullable = false, precision = 12, scale = 6)
    private BigDecimal bonusRate = BigDecimal.ZERO;

    @Column(name = "bonus_paid", nullable = false)
    private boolean bonusPaid = false;

    // Both -------------------------------------------------------------------

    /** How many separate deposits have gone into this plan. */
    @Column(name = "contributions", nullable = false)
    private int contributions = 0;

    /** How many auto-saves were skipped for a short wallet. */
    @Column(name = "missed_contributions", nullable = false)
    private int missedContributions = 0;

    @Column(name = "closed_at")
    private Instant closedAt;

    /** Why a plan was released early, when it was. */
    @Column(name = "closure_note", length = 500)
    private String closureNote;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    // Derived ----------------------------------------------------------------

    public boolean isFixed() {
        return type == SavingsType.FIXED;
    }

    public boolean isTarget() {
        return type == SavingsType.TARGET;
    }

    /** Still on the customer's books. */
    public boolean isOpen() {
        return status.isOpen();
    }

    public boolean isMature(Instant now) {
        return now.isAfter(maturityDate);
    }

    /**
     * Fixed plans are untouchable. Target plans can be broken any time while
     * they are still running.
     */
    public boolean canBreak() {
        return isTarget() && status == SavingsStatus.ACTIVE;
    }

    /** What the bonus is worth right now, on the money actually saved. */
    public BigDecimal bonusEarned() {
        return Money.multiply(principal, bonusRate);
    }

    /** What a completed plan would pay, on the full schedule. */
    public BigDecimal projectedBonus() {
        BigDecimal base = targetAmount == null ? principal : targetAmount;
        return Money.multiply(base, bonusRate);
    }

    /** Principal plus every naira of return already credited. */
    public BigDecimal totalValue() {
        return Money.add(principal, interestPaid);
    }

    /** What lands in the wallet if the plan runs to maturity. */
    public BigDecimal valueAtMaturity() {
        if (isFixed()) {
            return Money.of(principal);
        }
        BigDecimal base = targetAmount == null ? principal : targetAmount;
        return Money.add(base, projectedBonus());
    }

    /** Progress through the lock period, 0 to 1. */
    public double progress(Instant now) {
        long total = maturityDate.getEpochSecond() - startDate.getEpochSecond();
        if (total <= 0) {
            return 1;
        }
        long done = now.getEpochSecond() - startDate.getEpochSecond();
        return Math.max(0, Math.min(1, (double) done / total));
    }

    /** Progress towards the target, 0 to 1. Falls back to time for a Fixed plan. */
    public double goalProgress(Instant now) {
        if (targetAmount == null || targetAmount.signum() <= 0) {
            return progress(now);
        }
        return Math.max(0, Math.min(1,
                Money.divide(principal, targetAmount).doubleValue()));
    }

    public boolean goalReached() {
        return targetAmount != null && Money.gte(principal, targetAmount);
    }

    public BigDecimal amountLeftToTarget() {
        if (targetAmount == null) {
            return Money.zero();
        }
        return Money.floorAtZero(Money.subtract(targetAmount, principal));
    }

    public int daysRemaining(Instant now) {
        return com.quadrilateral.kudi9ja.common.util.Dates.daysBetween(now, maturityDate);
    }

    /** Whether an auto-save contribution has fallen due. */
    public boolean autoIsDue(Instant now) {
        return autoEnabled
                && nextAutoRun != null
                && now.isAfter(nextAutoRun)
                && status == SavingsStatus.ACTIVE;
    }
}
