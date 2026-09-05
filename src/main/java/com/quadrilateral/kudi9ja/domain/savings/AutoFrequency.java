package com.quadrilateral.kudi9ja.domain.savings;

import java.time.Duration;

/** How often a Target Savings contribution or a thrift round comes round. */
public enum AutoFrequency {

    DAILY(Duration.ofDays(1), "Daily", "every day", 365),
    WEEKLY(Duration.ofDays(7), "Weekly", "every week", 52),
    MONTHLY(Duration.ofDays(30), "Monthly", "every month", 12);

    private final Duration interval;
    private final String label;
    private final String adverb;
    private final int perYear;

    AutoFrequency(Duration interval, String label, String adverb, int perYear) {
        this.interval = interval;
        this.label = label;
        this.adverb = adverb;
        this.perYear = perYear;
    }

    public Duration interval() {
        return interval;
    }

    public String label() {
        return label;
    }

    public String adverb() {
        return adverb;
    }

    /** Contributions per year, used to project a goal's finish date. */
    public int perYear() {
        return perYear;
    }
}
