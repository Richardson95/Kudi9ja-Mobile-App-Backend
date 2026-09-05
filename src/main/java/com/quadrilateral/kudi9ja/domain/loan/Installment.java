package com.quadrilateral.kudi9ja.domain.loan;

import com.quadrilateral.kudi9ja.common.util.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * One row of a repayment schedule.
 *
 * <p>Derived, never stored. A stored schedule can drift away from the ledger;
 * a derived one cannot.
 */
public record Installment(
        int number,
        Instant dueDate,
        BigDecimal amount,
        BigDecimal amountPaid,
        InstallmentStatus status) {

    public BigDecimal outstanding() {
        BigDecimal left = Money.subtract(amount, amountPaid);
        return left.compareTo(new BigDecimal("0.01")) < 0 ? Money.zero() : left;
    }

    /** Negative once the date has passed. */
    public long daysUntilDue(Instant now) {
        return ChronoUnit.DAYS.between(now, dueDate);
    }
}
