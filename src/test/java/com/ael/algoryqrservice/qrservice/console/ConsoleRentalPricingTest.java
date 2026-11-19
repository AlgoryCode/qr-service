package com.ael.algoryqrservice.qrservice.console;

import com.ael.algoryqrservice.qrservice.console.domain.ConsoleRentalPricing;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ConsoleRentalPricingTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 19, 20, 0, 0);

    @Test
    void billableMinutes_countsAStartedMinuteAsFull() {
        assertThat(ConsoleRentalPricing.billableMinutes(START, START)).isZero();
        assertThat(ConsoleRentalPricing.billableMinutes(START, START.plusSeconds(1))).isEqualTo(1);
        assertThat(ConsoleRentalPricing.billableMinutes(START, START.plusMinutes(90))).isEqualTo(90);
        assertThat(ConsoleRentalPricing.billableMinutes(START, START.plusMinutes(90).plusSeconds(5))).isEqualTo(91);
    }

    @Test
    void billableMinutes_whenClockWentBackwards_isZero() {
        assertThat(ConsoleRentalPricing.billableMinutes(START, START.minusMinutes(3))).isZero();
    }

    @Test
    void amount_isProratedByTheMinute() {
        BigDecimal rate = new BigDecimal("120.00");
        assertThat(ConsoleRentalPricing.amount(rate, 60)).isEqualByComparingTo("120.00");
        assertThat(ConsoleRentalPricing.amount(rate, 90)).isEqualByComparingTo("180.00");
        assertThat(ConsoleRentalPricing.amount(rate, 1)).isEqualByComparingTo("2.00");
        assertThat(ConsoleRentalPricing.amount(rate, 0)).isEqualByComparingTo("0.00");
    }

    @Test
    void amount_roundsHalfUpToKurus() {
        // 100 TL/saat, 7 dk = 11.666... -> 11.67
        assertThat(ConsoleRentalPricing.amount(new BigDecimal("100"), 7)).isEqualByComparingTo("11.67");
    }
}
