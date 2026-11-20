package com.ael.algoryqrservice.qrservice.console.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

/**
 * Prices a session's play time.
 * <ul>
 *   <li>Each played stretch (segment) is charged at its own hourly rate, second by second.</li>
 *   <li>A time-of-day tariff that covers a moment adds its percentage to the rate for that moment. When several
 *   cover it, one for the device type wins over one for every type, then the lowest id.</li>
 *   <li>The played time is rounded up to whole minutes, then up to the rounding block, then raised to the minimum.
 *   The minutes added this way are charged at the rate in force when play stopped.</li>
 * </ul>
 * With a single stretch, no tariff, no minimum and 1-minute rounding this is exactly
 * {@code hourlyRate × startedMinutes / 60}.
 */
public final class DeviceRentalPricing {

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal SECONDS_PER_HOUR = BigDecimal.valueOf(3600);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private DeviceRentalPricing() {
    }

    public record Segment(LocalDateTime startedAt, LocalDateTime endedAt, BigDecimal hourlyRate, DeviceType deviceType) {
    }

    public record Tariff(Long id, DeviceType deviceType, int daysMask, LocalTime start, LocalTime end, BigDecimal adjustPercent) {

        boolean covers(LocalDateTime at, DeviceType type) {
            if (deviceType != null && deviceType != type) {
                return false;
            }
            int dayBit = 1 << (at.getDayOfWeek().getValue() - 1);
            if ((daysMask & dayBit) == 0) {
                return false;
            }
            LocalTime time = at.toLocalTime();
            if (end.isAfter(start)) {
                return !time.isBefore(start) && time.isBefore(end);
            }
            return !time.isBefore(start) || time.isBefore(end);
        }
    }

    public record Rules(int minimumMinutes, int roundingMinutes, List<Tariff> tariffs) {

        public static final Rules NONE = new Rules(0, 1, List.of());

        public Rules {
            minimumMinutes = Math.max(minimumMinutes, 0);
            roundingMinutes = Math.max(roundingMinutes, 1);
            tariffs = tariffs == null
                    ? List.of()
                    : tariffs.stream()
                            .sorted(Comparator.comparing((Tariff t) -> t.deviceType() == null)
                                    .thenComparing(t -> t.id() == null ? Long.MAX_VALUE : t.id()))
                            .toList();
        }
    }

    /** {@code playedSeconds} is the actual time on the devices; {@code billedMinutes} is what was charged. */
    public record Quote(long playedSeconds, int billedMinutes, BigDecimal amount) {
    }

    /** Open segments ({@code endedAt} null) are counted up to {@code now}. */
    public static Quote quote(List<Segment> segments, Rules rules, LocalDateTime now) {
        Rules r = rules == null ? Rules.NONE : rules;
        BigDecimal amount = BigDecimal.ZERO;
        long playedNanos = 0;
        Segment last = null;
        LocalDateTime lastEnd = null;
        for (Segment segment : segments) {
            LocalDateTime end = segment.endedAt() == null ? now : segment.endedAt();
            if (!end.isAfter(segment.startedAt())) {
                if (last == null) {
                    last = segment;
                    lastEnd = segment.startedAt();
                }
                continue;
            }
            LocalDateTime cursor = segment.startedAt();
            while (cursor.isBefore(end)) {
                LocalDateTime nextMinute = cursor.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1);
                LocalDateTime chunkEnd = nextMinute.isBefore(end) ? nextMinute : end;
                long nanos = Duration.between(cursor, chunkEnd).toNanos();
                BigDecimal seconds = BigDecimal.valueOf(nanos).movePointLeft(9);
                amount = amount.add(rateAt(segment, cursor, r).multiply(seconds, MC).divide(SECONDS_PER_HOUR, MC), MC);
                cursor = chunkEnd;
            }
            playedNanos += Duration.between(segment.startedAt(), end).toNanos();
            if (last == null || !end.isBefore(lastEnd)) {
                last = segment;
                lastEnd = end;
            }
        }
        long played = playedNanos / 1_000_000_000L;
        int billed = billedMinutes(played, r);
        BigDecimal extraSeconds = BigDecimal.valueOf(billed * 60L * 1_000_000_000L - playedNanos).movePointLeft(9);
        if (extraSeconds.signum() > 0 && last != null) {
            // Charge the rounded-up time at the rate in force when play stopped (the moment just before it).
            LocalDateTime at = lastEnd.isAfter(last.startedAt()) ? lastEnd.minusNanos(1) : last.startedAt();
            amount = amount.add(rateAt(last, at, r)
                    .multiply(extraSeconds, MC)
                    .divide(SECONDS_PER_HOUR, MC), MC);
        }
        return new Quote(played, billed, amount.setScale(2, RoundingMode.HALF_UP));
    }

    public static int billedMinutes(long playedSeconds, Rules rules) {
        Rules r = rules == null ? Rules.NONE : rules;
        long minutes = playedSeconds <= 0 ? 0 : (playedSeconds + 59) / 60;
        long block = r.roundingMinutes();
        minutes = ((minutes + block - 1) / block) * block;
        if (minutes < r.minimumMinutes()) {
            minutes = r.minimumMinutes();
        }
        return Math.toIntExact(minutes);
    }

    static BigDecimal rateAt(Segment segment, LocalDateTime at, Rules rules) {
        for (Tariff tariff : rules.tariffs()) {
            if (tariff.covers(at, segment.deviceType())) {
                BigDecimal factor = BigDecimal.ONE.add(tariff.adjustPercent().divide(HUNDRED, MC), MC);
                return segment.hourlyRate().multiply(factor, MC);
            }
        }
        return segment.hourlyRate();
    }

    /** The share of {@code timeAmount} that {@code freeMinutes} of {@code billedMinutes} are worth. */
    public static BigDecimal valueOfMinutes(BigDecimal timeAmount, int billedMinutes, int freeMinutes) {
        if (billedMinutes <= 0 || freeMinutes <= 0 || timeAmount.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        int used = Math.min(freeMinutes, billedMinutes);
        return timeAmount.multiply(BigDecimal.valueOf(used))
                .divide(BigDecimal.valueOf(billedMinutes), 2, RoundingMode.HALF_UP);
    }
}
