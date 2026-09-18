package com.ael.algoryqrservice.store.service;

import com.ael.algoryqrservice.store.model.Merchant;
import com.ael.algoryqrservice.store.model.MerchantStatus;
import com.ael.algoryqrservice.store.model.StoreWorkingHour;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StoreOpeningHoursTest {

    private static final LocalDateTime MONDAY_NOON = LocalDateTime.of(2026, 9, 21, 12, 0);
    private static final LocalDateTime MONDAY_LATE_NIGHT = LocalDateTime.of(2026, 9, 21, 1, 30);

    private final StoreOpeningHours storeOpeningHours = new StoreOpeningHours();

    @Test
    void isOpenAt_whenNoWorkingHoursConfigured_thenAlwaysOpen() {
        Merchant merchant = activeMerchant(List.of());

        assertThat(storeOpeningHours.isOpenAt(merchant, MONDAY_NOON)).isTrue();
    }

    @Test
    void isOpenAt_whenManuallyClosed_thenClosed() {
        Merchant merchant = activeMerchant(List.of());
        merchant.setManuallyClosed(true);

        assertThat(storeOpeningHours.isOpenAt(merchant, MONDAY_NOON)).isFalse();
    }

    @Test
    void isOpenAt_whenMerchantNotActive_thenClosed() {
        Merchant merchant = activeMerchant(List.of());
        merchant.setStatus(MerchantStatus.SUSPENDED);

        assertThat(storeOpeningHours.isOpenAt(merchant, MONDAY_NOON)).isFalse();
    }

    @Test
    void isOpenAt_whenInsideWindow_thenOpen() {
        Merchant merchant = activeMerchant(List.of(window(DayOfWeek.MONDAY, 9, 22)));

        assertThat(storeOpeningHours.isOpenAt(merchant, MONDAY_NOON)).isTrue();
    }

    @Test
    void isOpenAt_whenOutsideWindow_thenClosed() {
        Merchant merchant = activeMerchant(List.of(window(DayOfWeek.MONDAY, 14, 22)));

        assertThat(storeOpeningHours.isOpenAt(merchant, MONDAY_NOON)).isFalse();
    }

    @Test
    void isOpenAt_whenWindowCrossesMidnight_thenOpenAfterMidnight() {
        Merchant merchant = activeMerchant(List.of(window(DayOfWeek.MONDAY, 18, 3)));

        assertThat(storeOpeningHours.isOpenAt(merchant, MONDAY_LATE_NIGHT)).isTrue();
    }

    @Test
    void isOpenAt_whenDayMarkedClosed_thenClosed() {
        StoreWorkingHour closedDay = StoreWorkingHour.builder()
                .day(DayOfWeek.MONDAY)
                .opensAt(LocalTime.of(9, 0))
                .closesAt(LocalTime.of(22, 0))
                .closed(true)
                .build();

        assertThat(storeOpeningHours.isOpenAt(activeMerchant(List.of(closedDay)), MONDAY_NOON)).isFalse();
    }

    private StoreWorkingHour window(DayOfWeek day, int opensAtHour, int closesAtHour) {
        return StoreWorkingHour.builder()
                .day(day)
                .opensAt(LocalTime.of(opensAtHour, 0))
                .closesAt(LocalTime.of(closesAtHour, 0))
                .closed(false)
                .build();
    }

    private Merchant activeMerchant(List<StoreWorkingHour> workingHours) {
        return Merchant.builder()
                .status(MerchantStatus.ACTIVE)
                .workingHours(workingHours)
                .build();
    }
}
