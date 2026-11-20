package com.ael.algoryqrservice.qrservice.console;

import com.ael.algoryqrservice.qrservice.console.domain.DeviceRentalPricing;
import com.ael.algoryqrservice.qrservice.console.domain.DeviceRentalPricing.Quote;
import com.ael.algoryqrservice.qrservice.console.domain.DeviceRentalPricing.Rules;
import com.ael.algoryqrservice.qrservice.console.domain.DeviceRentalPricing.Segment;
import com.ael.algoryqrservice.qrservice.console.domain.DeviceRentalPricing.Tariff;
import com.ael.algoryqrservice.qrservice.console.domain.DeviceType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceRentalPricingTest {

    /** A Friday. */
    private static final LocalDateTime FRI_20 = LocalDateTime.of(2026, 11, 20, 20, 0);
    private static final int ALL_DAYS = 0b1111111;
    private static final int WEEKEND = 0b1100000;
    private static final BigDecimal RATE_120 = new BigDecimal("120.00");

    private static Segment ps(LocalDateTime start, LocalDateTime end, BigDecimal rate) {
        return new Segment(start, end, rate, DeviceType.PLAYSTATION);
    }

    @Test
    void singleStretch_noRules_matchesTheOldPerMinuteCharge() {
        Quote quote = DeviceRentalPricing.quote(
                List.of(ps(FRI_20, FRI_20.plusMinutes(95).plusSeconds(20), RATE_120)), Rules.NONE, FRI_20);

        assertThat(quote.billedMinutes()).isEqualTo(96);
        assertThat(quote.amount()).isEqualByComparingTo("192.00");
        assertThat(quote.playedSeconds()).isEqualTo(95 * 60 + 20);
    }

    @Test
    void openStretch_runsUntilNow() {
        Quote quote = DeviceRentalPricing.quote(List.of(ps(FRI_20, null, RATE_120)), Rules.NONE, FRI_20.plusMinutes(30));

        assertThat(quote.billedMinutes()).isEqualTo(30);
        assertThat(quote.amount()).isEqualByComparingTo("60.00");
    }

    @Test
    void pausedGap_isNotCharged_andEachDeviceKeepsItsRate() {
        Quote quote = DeviceRentalPricing.quote(List.of(
                ps(FRI_20, FRI_20.plusMinutes(60), RATE_120),
                // 15-minute pause, then 30 minutes on a PC at 40/h
                new Segment(FRI_20.plusMinutes(75), FRI_20.plusMinutes(105), new BigDecimal("40.00"), DeviceType.PC)
        ), Rules.NONE, FRI_20);

        assertThat(quote.billedMinutes()).isEqualTo(90);
        assertThat(quote.amount()).isEqualByComparingTo("140.00");
    }

    @Test
    void tariff_appliesOnlyInsideItsWindow() {
        // From 21:00 everything is 50% dearer.
        Tariff evening = new Tariff(1L, null, ALL_DAYS, LocalTime.of(21, 0), LocalTime.of(23, 59), new BigDecimal("50"));
        Quote quote = DeviceRentalPricing.quote(
                List.of(ps(FRI_20.plusMinutes(30), FRI_20.plusMinutes(90), RATE_120)),
                new Rules(0, 1, List.of(evening)),
                FRI_20);

        // 30 min at 120/h = 60, 30 min at 180/h = 90
        assertThat(quote.amount()).isEqualByComparingTo("150.00");
    }

    @Test
    void tariff_pastMidnight_coversBothSides() {
        Tariff night = new Tariff(1L, null, ALL_DAYS, LocalTime.of(23, 0), LocalTime.of(6, 0), new BigDecimal("-50"));
        LocalDateTime start = FRI_20.withHour(22).withMinute(30);
        Quote quote = DeviceRentalPricing.quote(
                List.of(ps(start, start.plusHours(2), RATE_120)), new Rules(0, 1, List.of(night)), start);

        // 30 min at 120 = 60, 90 min at 60 = 90
        assertThat(quote.amount()).isEqualByComparingTo("150.00");
    }

    @Test
    void tariff_forTheDeviceType_winsOverOneForEveryType_andOtherDaysDoNotApply() {
        Tariff all = new Tariff(1L, null, ALL_DAYS, LocalTime.of(0, 0), LocalTime.of(23, 59), new BigDecimal("100"));
        Tariff pc = new Tariff(2L, DeviceType.PC, ALL_DAYS, LocalTime.of(0, 0), LocalTime.of(23, 59), new BigDecimal("-25"));
        Tariff weekend = new Tariff(3L, DeviceType.PLAYSTATION, WEEKEND, LocalTime.of(0, 0), LocalTime.of(23, 59), new BigDecimal("400"));
        Rules rules = new Rules(0, 1, List.of(all, pc, weekend));

        Quote pcQuote = DeviceRentalPricing.quote(List.of(
                new Segment(FRI_20, FRI_20.plusHours(1), new BigDecimal("40.00"), DeviceType.PC)), rules, FRI_20);
        Quote psQuote = DeviceRentalPricing.quote(List.of(ps(FRI_20, FRI_20.plusHours(1), RATE_120)), rules, FRI_20);

        assertThat(pcQuote.amount()).isEqualByComparingTo("30.00");
        // Friday: the weekend rule does not apply, the all-types +100% does.
        assertThat(psQuote.amount()).isEqualByComparingTo("240.00");
    }

    @Test
    void rounding_andMinimum_chargeTheAddedMinutesAtTheLastRate() {
        Quote rounded = DeviceRentalPricing.quote(
                List.of(ps(FRI_20, FRI_20.plusMinutes(7), RATE_120)), new Rules(0, 15, List.of()), FRI_20);
        Quote minimum = DeviceRentalPricing.quote(
                List.of(ps(FRI_20, FRI_20.plusMinutes(7), RATE_120)), new Rules(30, 15, List.of()), FRI_20);
        Quote longer = DeviceRentalPricing.quote(
                List.of(ps(FRI_20, FRI_20.plusMinutes(46), RATE_120)), new Rules(30, 15, List.of()), FRI_20);

        assertThat(rounded.billedMinutes()).isEqualTo(15);
        assertThat(rounded.amount()).isEqualByComparingTo("30.00");
        assertThat(minimum.billedMinutes()).isEqualTo(30);
        assertThat(minimum.amount()).isEqualByComparingTo("60.00");
        assertThat(longer.billedMinutes()).isEqualTo(60);
        assertThat(longer.amount()).isEqualByComparingTo("120.00");
    }

    @Test
    void noPlay_isFree_unlessThereIsAMinimum() {
        assertThat(DeviceRentalPricing.quote(List.of(ps(FRI_20, FRI_20, RATE_120)), Rules.NONE, FRI_20).amount())
                .isEqualByComparingTo("0");
        assertThat(DeviceRentalPricing.quote(List.of(ps(FRI_20, FRI_20, RATE_120)), new Rules(30, 1, List.of()), FRI_20)
                .amount()).isEqualByComparingTo("60.00");
    }

    @Test
    void valueOfMinutes_isTheShareOfTheTimeAmount() {
        assertThat(DeviceRentalPricing.valueOfMinutes(new BigDecimal("192.00"), 96, 60)).isEqualByComparingTo("120.00");
        assertThat(DeviceRentalPricing.valueOfMinutes(new BigDecimal("192.00"), 96, 500)).isEqualByComparingTo("192.00");
        assertThat(DeviceRentalPricing.valueOfMinutes(new BigDecimal("192.00"), 96, 0)).isEqualByComparingTo("0");
    }
}
