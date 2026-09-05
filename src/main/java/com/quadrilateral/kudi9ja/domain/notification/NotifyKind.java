package com.quadrilateral.kudi9ja.domain.notification;

/**
 * What a notification is about.
 *
 * <p>The kind does three jobs. It tells the app which icon and colour to draw,
 * it decides whether the customer may switch these off, and it decides what may
 * appear on a locked phone.
 */
public enum NotifyKind {

    /** Interest or a bonus was paid into the wallet. */
    INTEREST("Savings returns", true),

    /** A savings plan reached its maturity date. */
    MATURITY("Plan maturity", true),

    /** An instalment is coming up, or has passed. */
    REPAYMENT_DUE("Repayment reminders", true),

    /** A repayment landed. */
    REPAYMENT_PAID("Repayment receipts", true),

    /** A scheduled contribution was collected, or skipped for a short balance. */
    AUTO_SAVE("Auto-save", true),

    /** Anything about a thrift circle. */
    THRIFT("Thrift circles", true),

    /**
     * Something changed that affects who can reach the money.
     *
     * <p><b>Cannot be switched off.</b> A payout account moving, an account
     * being frozen, a password changing — these are how a customer finds out
     * their account is in somebody else's hands, and an attacker who could
     * silence them first would silence them first.
     */
    SECURITY("Security alerts", false),

    /**
     * Money moved: a payment confirmed, a withdrawal approved, a loan
     * disbursed.
     *
     * <p>Also not optional. A customer who has turned off the message that
     * says their money arrived has turned off the product.
     */
    GENERAL("Money movements", false);

    private final String label;
    private final boolean optional;

    NotifyKind(String label, boolean optional) {
        this.label = label;
        this.optional = optional;
    }

    /** What this group is called on the customer's notification settings screen. */
    public String label() {
        return label;
    }

    /**
     * Whether the customer may turn this group off.
     *
     * <p>Most are optional — somebody who does not want to hear about every
     * auto-save should not have to. Security alerts and money movements are
     * not, because switching those off is indistinguishable from not being
     * told.
     */
    public boolean optional() {
        return optional;
    }

    /**
     * What may be shown on a locked phone.
     *
     * <p>Push notifications travel through Google and Apple and land on a
     * screen anybody standing nearby can read. So the amount never goes: the
     * lock screen says something happened and the app says what. "Your payment
     * was confirmed" tells the right person enough to open the app, and tells
     * the person behind them in the queue nothing worth having.
     *
     * <p>The one exception is a security alert, which is useless if it does not
     * say what happened — a customer who reads "Something changed" and taps
     * later has already lost the minutes that mattered.
     */
    public String lockScreenBody(String fullBody) {
        return this == SECURITY ? fullBody : "Open Kudi9ja to see the details.";
    }
}
