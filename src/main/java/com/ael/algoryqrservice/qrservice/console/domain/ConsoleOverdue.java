package com.ael.algoryqrservice.qrservice.console.domain;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * When an open session counts as forgotten: it ran {@link #PLANNED_GRACE_MINUTES} past the time the customer asked
 * for, it has no planned time and ran {@link #OPEN_ENDED_LIMIT_MINUTES}, or it has been paused for
 * {@link #PAUSED_LIMIT_MINUTES}.
 */
public final class ConsoleOverdue {

    public static final int PLANNED_GRACE_MINUTES = 10;
    public static final int OPEN_ENDED_LIMIT_MINUTES = 180;
    public static final int PAUSED_LIMIT_MINUTES = 60;

    public enum Reason {
        PLANNED_EXCEEDED,
        OPEN_TOO_LONG,
        PAUSED_TOO_LONG
    }

    private ConsoleOverdue() {
    }

    /** Null when the session is fine. */
    public static Reason check(Integer plannedMinutes, long playedSeconds, LocalDateTime pausedAt, LocalDateTime now) {
        if (plannedMinutes != null && playedSeconds > (plannedMinutes + PLANNED_GRACE_MINUTES) * 60L) {
            return Reason.PLANNED_EXCEEDED;
        }
        if (plannedMinutes == null && playedSeconds > OPEN_ENDED_LIMIT_MINUTES * 60L) {
            return Reason.OPEN_TOO_LONG;
        }
        if (pausedAt != null && Duration.between(pausedAt, now).toMinutes() >= PAUSED_LIMIT_MINUTES) {
            return Reason.PAUSED_TOO_LONG;
        }
        return null;
    }

    /** Minutes past the limit that made it overdue (past the planned end, past 3 hours, or paused for). */
    public static long overMinutes(Reason reason, Integer plannedMinutes, long playedSeconds, LocalDateTime pausedAt,
                                   LocalDateTime now) {
        return switch (reason) {
            case PLANNED_EXCEEDED -> Math.max(playedSeconds / 60 - plannedMinutes, 0);
            case OPEN_TOO_LONG -> Math.max(playedSeconds / 60 - OPEN_ENDED_LIMIT_MINUTES, 0);
            case PAUSED_TOO_LONG -> pausedAt == null ? 0 : Math.max(Duration.between(pausedAt, now).toMinutes(), 0);
        };
    }
}
