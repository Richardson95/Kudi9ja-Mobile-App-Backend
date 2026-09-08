package com.quadrilateral.kudi9ja.web.dto;

import com.quadrilateral.kudi9ja.domain.savings.AutoFrequency;
import com.quadrilateral.kudi9ja.domain.savings.SavingsPlan;
import com.quadrilateral.kudi9ja.domain.savings.SavingsStatus;
import com.quadrilateral.kudi9ja.domain.savings.SavingsType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Opening, topping up and closing a savings plan, and quoting one first. */
public final class SavingsDtos {

    private SavingsDtos() {
    }

    /**
     * Opening a Fixed plan.
     *
     * @param days the lock, in days. Nothing rounds to whole months: a customer
     *             who wants 171 days gets 171 days, priced exactly.
     */
    public record CreateFixedRequest(
            @NotBlank(message = "Give the plan a name.")
            @Size(max = 120, message = "Keep the plan name under 120 characters.")
            String title,

            @NotNull(message = "How much are you locking away?")
            @DecimalMin(value = "0.01", message = "An amount must be above zero.")
            BigDecimal principal,

            @NotNull(message = "How long for?")
            @Min(value = 1, message = "A lock runs for at least a day.")
            Integer days,

            String emoji,

            @NotBlank(message = "Your PIN is needed.")
            String pin) {
    }

    /** Opening a Target plan. */
    public record CreateTargetRequest(
            @NotBlank(message = "Give the plan a name.")
            @Size(max = 120, message = "Keep the plan name under 120 characters.")
            String title,

            @NotNull(message = "What are you saving towards?")
            @DecimalMin(value = "0.01", message = "A goal must be above zero.")
            BigDecimal goal,

            @NotNull(message = "How often should we save?")
            AutoFrequency frequency,

            @NotNull(message = "How many months?")
            @Min(value = 1, message = "A target plan runs for at least a month.")
            @Max(value = 120, message = "A target plan runs for at most ten years.")
            Integer months,

            String emoji,

            @NotBlank(message = "Your PIN is needed.")
            String pin) {
    }

    public record TopUpRequest(
            @NotNull @DecimalMin(value = "0.01", message = "An amount must be above zero.")
            BigDecimal amount,

            @NotBlank(message = "Your PIN is needed.")
            String pin) {
    }

    public record PlanPinRequest(
            @NotBlank(message = "Your PIN is needed.")
            String pin) {
    }

    /** Turning an auto-save schedule on or off, or changing what it moves. */
    public record AutoSaveRequest(
            @NotNull Boolean enabled,

            @DecimalMin(value = "0.01", message = "An amount must be above zero.")
            BigDecimal amount,

            AutoFrequency frequency) {
    }

    /** A plan, as the app shows it. Every derived figure is computed here. */
    public record PlanResponse(
            UUID id,
            String title,
            SavingsType type,
            String typeLabel,
            SavingsStatus status,
            String statusLabel,
            BigDecimal principal,
            int lockDays,
            BigDecimal interestPaid,
            BigDecimal annualRate,
            Instant startDate,
            Instant maturityDate,
            String emoji,
            BigDecimal targetAmount,
            Integer targetMonths,
            AutoFrequency autoFrequency,
            BigDecimal autoAmount,
            Instant nextAutoRun,
            boolean autoEnabled,
            BigDecimal bonusRate,
            boolean bonusPaid,
            BigDecimal bonusEarned,
            BigDecimal projectedBonus,
            int contributions,
            int missedContributions,
            BigDecimal totalValue,
            BigDecimal valueAtMaturity,
            BigDecimal amountLeftToTarget,
            boolean goalReached,
            double progress,
            double goalProgress,
            int daysRemaining,
            boolean mature,
            boolean canBreak,
            boolean canWithdraw) {

        public static PlanResponse from(SavingsPlan plan, Instant now) {
            boolean mature = plan.isMature(now) || plan.getStatus() == SavingsStatus.MATURED;
            return new PlanResponse(
                    plan.getId(),
                    plan.getTitle(),
                    plan.getType(),
                    plan.getType().label(),
                    plan.getStatus(),
                    plan.getStatus().label(),
                    plan.getPrincipal(),
                    plan.getLockDays(),
                    plan.getInterestPaid(),
                    plan.getAnnualRate(),
                    plan.getStartDate(),
                    plan.getMaturityDate(),
                    plan.getEmoji(),
                    plan.getTargetAmount(),
                    plan.getTargetMonths(),
                    plan.getAutoFrequency(),
                    plan.getAutoAmount(),
                    plan.getNextAutoRun(),
                    plan.isAutoEnabled(),
                    plan.getBonusRate(),
                    plan.isBonusPaid(),
                    plan.bonusEarned(),
                    plan.projectedBonus(),
                    plan.getContributions(),
                    plan.getMissedContributions(),
                    plan.totalValue(),
                    plan.valueAtMaturity(),
                    plan.amountLeftToTarget(),
                    plan.goalReached(),
                    plan.progress(now),
                    plan.goalProgress(now),
                    plan.daysRemaining(now),
                    mature,
                    plan.canBreak(),
                    plan.isOpen() && mature);
        }
    }

    /**
     * What a Fixed plan would cost and pay, before the customer commits.
     *
     * @param interest paid into the wallet the moment the plan is created
     */
    public record FixedQuoteResponse(
            BigDecimal principal,
            int days,
            BigDecimal annualRate,
            BigDecimal interest,
            BigDecimal total,
            BigDecimal effectiveYieldPct,
            Instant maturityDate,
            boolean withinLimits,
            String note) {
    }

    /** What a Target plan asks for and pays. */
    public record TargetQuoteResponse(
            BigDecimal goal,
            int months,
            AutoFrequency frequency,
            int runs,
            BigDecimal perDeposit,
            BigDecimal bonusRate,
            BigDecimal projectedBonus,
            BigDecimal totalAtMaturity,
            Instant maturityDate,
            boolean withinLimits,
            String note) {
    }

    /** What happened when a plan was released. */
    public record ReleaseResponse(
            UUID planId,
            BigDecimal principalReturned,
            BigDecimal bonusPaid,
            BigDecimal totalCredited,
            BigDecimal newBalance,
            SavingsStatus status,
            String message) {
    }
}
