package com.quadrilateral.kudi9ja.domain.savings;

/**
 * The two ways to save on Kudi9ja. They differ in when the return is paid and
 * whether the plan can be broken at all.
 */
public enum SavingsType {

    /**
     * One lump sum. Pays the full annual return into the wallet the instant it
     * starts, and can never be broken — the return is paid upfront precisely
     * because the principal stays put. The only early release is death or
     * permanent incapacity, which is an admin action, not a customer one.
     */
    FIXED("Fixed Savings", "One amount locked away. The return is paid to you today."),

    /**
     * Contributions pulled from the wallet on a schedule. Pays a bonus on the
     * final day, and can be broken at any time — but breaking it forfeits the
     * entire bonus. Every naira saved comes back in full.
     */
    TARGET("Target Savings", "Save a set amount daily, weekly or monthly. Bonus on the last day.");

    private final String label;
    private final String blurb;

    SavingsType(String label, String blurb) {
        this.label = label;
        this.blurb = blurb;
    }

    public String label() {
        return label;
    }

    public String blurb() {
        return blurb;
    }

    /** Fixed plans cannot be broken under any circumstance. */
    public boolean canBreak() {
        return this == TARGET;
    }
}
