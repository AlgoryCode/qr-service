package com.ael.algoryqrservice.util;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.TimeZone;

/**
 * Application business time. Purchase/entitlement timestamps are stored as
 * {@code timestamp without time zone} using Europe/Istanbul wall clock.
 */
public final class AppTime {

    public static final ZoneId ZONE = ZoneId.of("Europe/Istanbul");
    public static final String ZONE_ID = "Europe/Istanbul";

    private static volatile Clock clock = Clock.system(ZONE);

    private AppTime() {
    }

    public static void initializeDefaultTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone(ZONE_ID));
    }

    public static LocalDateTime nowLocal() {
        return LocalDateTime.now(clock);
    }

    public static Clock clock() {
        return clock;
    }

    public static void setClock(Clock newClock) {
        clock = newClock;
    }

    public static void resetClock() {
        clock = Clock.system(ZONE);
    }
}
