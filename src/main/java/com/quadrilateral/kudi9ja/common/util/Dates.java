package com.quadrilateral.kudi9ja.common.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Date arithmetic shared by savings maturity, loan schedules and the
 * collections window.
 *
 * <p>Everything is reckoned in West Africa Time. A repayment falls due on a
 * Lagos date, not a UTC one, and the collections curfew runs on a Lagos clock.
 */
public final class Dates {

    /** Nigeria has no daylight saving; WAT is UTC+1 all year. */
    public static final ZoneId LAGOS = ZoneId.of("Africa/Lagos");

    private Dates() {
    }

    public static LocalDate lagosDate(Instant instant) {
        return instant.atZone(LAGOS).toLocalDate();
    }

    public static LocalTime lagosTime(Instant instant) {
        return instant.atZone(LAGOS).toLocalTime();
    }

    /**
     * Adds calendar months, clamping the day to the target month's end, so a
     * loan disbursed on 31 January falls due on 28 February.
     */
    public static Instant addMonths(Instant from, int months) {
        return from.atZone(LAGOS).plusMonths(months).toInstant();
    }

    public static Instant addDays(Instant from, int days) {
        return from.plus(days, ChronoUnit.DAYS);
    }

    /**
     * Whole months elapsed between two instants: the count an early settlement
     * rebate is worked out from.
     */
    public static int monthsBetween(Instant from, Instant to) {
        ZonedDateTime a = from.atZone(LAGOS);
        ZonedDateTime b = to.atZone(LAGOS);
        int months = (b.getYear() - a.getYear()) * 12 + (b.getMonthValue() - a.getMonthValue());
        if (b.getDayOfMonth() < a.getDayOfMonth()) {
            months -= 1;
        }
        return months;
    }

    /** Whole days between two instants, floored at zero. */
    public static int daysBetween(Instant from, Instant to) {
        long days = ChronoUnit.DAYS.between(from, to);
        return days < 0 ? 0 : (int) days;
    }

    /** Whole days between two instants, which may be negative. */
    public static long signedDaysBetween(Instant from, Instant to) {
        return ChronoUnit.DAYS.between(from, to);
    }

    public static boolean isAfter(Instant a, Instant b) {
        return a != null && b != null && a.isAfter(b);
    }

    public static Instant startOfLagosDay(Instant instant) {
        return instant.atZone(LAGOS).toLocalDate().atStartOfDay(LAGOS).toInstant();
    }

    public static Instant endOfLagosDay(Instant instant) {
        return instant.atZone(LAGOS).toLocalDate().plusDays(1).atStartOfDay(LAGOS).toInstant();
    }
}
