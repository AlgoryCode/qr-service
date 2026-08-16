package com.ael.algoryqrservice.model;

import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.util.AppTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseTimezoneTest {

    private static final ZoneId ISTANBUL = AppTime.ZONE;

    @AfterEach
    void resetClockAndTimeZone() {
        AppTime.resetClock();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @Test
    void isUsable_whenStoredIstanbulWallClockAndJvmIsUtc_thenTreatAsStarted() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        AppTime.setClock(Clock.fixed(
                LocalDateTime.of(2026, 8, 15, 12, 59, 0).atZone(ISTANBUL).toInstant(),
                ISTANBUL
        ));

        Purchase purchase = Purchase.builder()
                .status(PurchaseStatus.ACTIVE)
                .startsAt(LocalDateTime.of(2026, 8, 15, 11, 42, 23))
                .expiresAt(LocalDateTime.of(2026, 9, 14, 11, 42, 23))
                .build();

        assertThat(purchase.isUsable()).isTrue();
    }

    @Test
    void isUsable_whenTrialStartsLaterSameDay_thenBecomesUsableAfterIstanbulClockPasses() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        Purchase purchase = Purchase.builder()
                .status(PurchaseStatus.ACTIVE)
                .startsAt(LocalDateTime.of(2026, 8, 15, 11, 42, 23))
                .expiresAt(LocalDateTime.of(2026, 9, 14, 11, 42, 23))
                .build();

        AppTime.setClock(Clock.fixed(
                LocalDateTime.of(2026, 8, 15, 9, 44, 30).atZone(ISTANBUL).toInstant(),
                ISTANBUL
        ));
        assertThat(purchase.isUsable()).isFalse();

        AppTime.setClock(Clock.fixed(
                LocalDateTime.of(2026, 8, 15, 12, 44, 30).atZone(ISTANBUL).toInstant(),
                ISTANBUL
        ));
        assertThat(purchase.isUsable()).isTrue();
    }
}
