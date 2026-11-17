package com.ael.algoryqrservice.qrservice.analytics.assistant;

/**
 * What a dashboard question was about, with the minutes a merchant would otherwise spend finding the
 * same answer by hand (opening the report, choosing the period, reading the numbers). These are
 * conservative estimates shown as such in the report, not measurements.
 */
public enum AssistantUsageTopic {
    REVENUE(4),
    ORDERS(3),
    PRODUCTS(3),
    STAFF(3),
    MENU(2),
    GENERAL(2);

    private final int minutesSaved;

    AssistantUsageTopic(int minutesSaved) {
        this.minutesSaved = minutesSaved;
    }

    public int minutesSaved() {
        return minutesSaved;
    }
}
