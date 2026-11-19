package com.ael.algoryqrservice.qrservice.console.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * How a console rental is charged: the duration is counted in whole minutes, a started minute counting
 * as a full one, and the amount is the hourly rate prorated by the minute, rounded to kuruş.
 */
public final class ConsoleRentalPricing {

    private static final BigDecimal MINUTES_PER_HOUR = BigDecimal.valueOf(60);

    private ConsoleRentalPricing() {
    }

    public static int billableMinutes(LocalDateTime startedAt, LocalDateTime endedAt) {
        long seconds = Duration.between(startedAt, endedAt).getSeconds();
        if (seconds <= 0) {
            return 0;
        }
        return Math.toIntExact((seconds + 59) / 60);
    }

    public static BigDecimal amount(BigDecimal hourlyRate, int minutes) {
        return hourlyRate
                .multiply(BigDecimal.valueOf(minutes))
                .divide(MINUTES_PER_HOUR, 2, RoundingMode.HALF_UP);
    }

    public static BigDecimal scaleRate(BigDecimal rate) {
        return rate == null ? null : rate.setScale(2, RoundingMode.HALF_UP);
    }
}
