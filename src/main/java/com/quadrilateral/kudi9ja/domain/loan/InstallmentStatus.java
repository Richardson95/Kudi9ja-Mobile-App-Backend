package com.quadrilateral.kudi9ja.domain.loan;

/** One row of a repayment schedule, as derived from what has been repaid. */
public enum InstallmentStatus {

    PAID("Paid"),
    PARTIAL("Part paid"),
    UPCOMING("Upcoming"),
    OVERDUE("Overdue");

    private final String label;

    InstallmentStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /**
     * Whether this row is finished with.
     *
     * <p>Only {@code PAID} is. A part-paid instalment still has money owing on
     * it, so it is what the customer is asked for next — reminding someone
     * about the instalment after it while one is still short would be wrong.
     */
    public boolean isSettled() {
        return this == PAID;
    }
}
