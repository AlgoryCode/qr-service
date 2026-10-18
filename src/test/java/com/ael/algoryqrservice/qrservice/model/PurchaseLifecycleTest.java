package com.ael.algoryqrservice.qrservice.model;

import com.ael.algoryqrservice.fulfillmentservice.domain.model.UserEntitlement;

import com.ael.algoryqrservice.qrservice.model.enums.PurchaseStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseLifecycleTest {

    @Test
    void isUsable_whenActiveAndExpiresInFuture_thenTrue() {
        Purchase purchase = Purchase.builder()
                .status(PurchaseStatus.ACTIVE)
                .startsAt(LocalDateTime.now().minusDays(1))
                .expiresAt(LocalDateTime.now().plusDays(10))
                .build();

        assertThat(purchase.isUsable()).isTrue();
        assertThat(purchase.isEffectivelyExpired()).isFalse();
    }

    @Test
    void isUsable_whenActiveButExpiresAtPassed_thenFalse() {
        Purchase purchase = Purchase.builder()
                .status(PurchaseStatus.ACTIVE)
                .startsAt(LocalDateTime.now().minusDays(30))
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .build();

        assertThat(purchase.isUsable()).isFalse();
        assertThat(purchase.isExpiredByDate()).isTrue();
        assertThat(purchase.isEffectivelyExpired()).isTrue();
    }

    @Test
    void isUsable_whenActiveButNotStarted_thenFalse() {
        Purchase purchase = Purchase.builder()
                .status(PurchaseStatus.ACTIVE)
                .startsAt(LocalDateTime.now().plusDays(1))
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();

        assertThat(purchase.isUsable()).isFalse();
    }

    @Test
    void isUsable_whenActiveWithoutExpiresAt_thenFalse() {
        Purchase purchase = Purchase.builder()
                .status(PurchaseStatus.ACTIVE)
                .startsAt(LocalDateTime.now().minusDays(1))
                .expiresAt(null)
                .build();

        assertThat(purchase.isUsable()).isFalse();
    }

}
