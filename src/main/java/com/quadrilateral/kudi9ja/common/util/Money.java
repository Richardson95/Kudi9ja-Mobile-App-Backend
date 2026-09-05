package com.quadrilateral.kudi9ja.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Naira arithmetic. Every amount in the system is a {@link BigDecimal} scaled
 * to kobo, so a ledger can be summed without the drift a double would bring.
 *
 * <p>The ledger is authoritative: a wallet balance must be reconstructible by
 * replaying its transactions, and that only holds if every figure rounds the
 * same way, once, at the same scale.
 */
public final class Money {

    /** Kobo. Two places is what a naira account statement shows. */
    public static final int SCALE = 2;

    /**
     * The scale interest is computed at before it is rounded to kobo. Savings
     * interest is a rate times a day count; keeping the intermediate wide stops
     * a 171-day plan drifting from the published yield.
     */
    public static final int CALC_SCALE = 10;

    private Money() {
    }

    public static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
    }

    /** Rounds to kobo, half up: the rounding a customer expects to see. */
    public static BigDecimal of(BigDecimal value) {
        return value == null ? zero() : value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal of(double value) {
        return of(BigDecimal.valueOf(value));
    }

    public static BigDecimal of(long value) {
        return of(BigDecimal.valueOf(value));
    }

    public static BigDecimal of(String value) {
        return of(new BigDecimal(value));
    }

    /** Wide scale, for an intermediate that will be rounded later. */
    public static BigDecimal calc(BigDecimal value) {
        return nz(value).setScale(CALC_SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal add(BigDecimal a, BigDecimal b) {
        return of(nz(a).add(nz(b)));
    }

    public static BigDecimal subtract(BigDecimal a, BigDecimal b) {
        return of(nz(a).subtract(nz(b)));
    }

    public static BigDecimal multiply(BigDecimal a, BigDecimal b) {
        return of(nz(a).multiply(nz(b)));
    }

    /** Divides at calculation scale; round with {@link #of} when it settles. */
    public static BigDecimal divide(BigDecimal a, BigDecimal b) {
        if (b == null || b.signum() == 0) {
            return zero();
        }
        return nz(a).divide(b, CALC_SCALE, RoundingMode.HALF_UP);
    }

    /** Never below zero, which is what an outstanding balance needs. */
    public static BigDecimal floorAtZero(BigDecimal value) {
        BigDecimal v = of(value);
        return v.signum() < 0 ? zero() : v;
    }

    public static BigDecimal min(BigDecimal a, BigDecimal b) {
        return nz(a).compareTo(nz(b)) <= 0 ? of(a) : of(b);
    }

    public static BigDecimal max(BigDecimal a, BigDecimal b) {
        return nz(a).compareTo(nz(b)) >= 0 ? of(a) : of(b);
    }

    public static boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    public static boolean isZeroOrLess(BigDecimal value) {
        return value == null || value.signum() <= 0;
    }

    public static boolean gt(BigDecimal a, BigDecimal b) {
        return nz(a).compareTo(nz(b)) > 0;
    }

    public static boolean gte(BigDecimal a, BigDecimal b) {
        return nz(a).compareTo(nz(b)) >= 0;
    }

    public static boolean lt(BigDecimal a, BigDecimal b) {
        return nz(a).compareTo(nz(b)) < 0;
    }

    public static boolean lte(BigDecimal a, BigDecimal b) {
        return nz(a).compareTo(nz(b)) <= 0;
    }

    public static boolean eq(BigDecimal a, BigDecimal b) {
        return nz(a).compareTo(nz(b)) == 0;
    }

    /**
     * Rounds down to the nearest multiple of {@code step}, which is how a loan
     * offer is presented in whole steps of five thousand rather than to the
     * naira.
     */
    public static BigDecimal floorTo(BigDecimal value, BigDecimal step) {
        if (step == null || step.signum() <= 0) {
            return of(value);
        }
        return of(nz(value).divide(step, 0, RoundingMode.FLOOR).multiply(step));
    }

    /** A plain figure for a notification body: no currency symbol, no decimals. */
    public static String plain(BigDecimal value) {
        return of(value).setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    /** The naira form used in customer-facing copy. */
    public static String naira(BigDecimal value) {
        return "₦" + of(value).toPlainString();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
