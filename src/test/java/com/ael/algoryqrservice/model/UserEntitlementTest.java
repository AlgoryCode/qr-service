package com.ael.algoryqrservice.model;

import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class UserEntitlementTest {

    @Test
    void grantsScope_whenFiniteEntitlementHasNoRemainingQuantity_thenReturnTrueIfTotalQuantityPositive() {
        UserEntitlement entitlement = UserEntitlement.builder()
                .remainingQuantity(0)
                .totalQuantity(1)
                .usedQuantity(1)
                .unlimited(false)
                .startsAt(LocalDateTime.now().minusDays(1))
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();
        Purchase purchase = Purchase.builder()
                .status(PurchaseStatus.ACTIVE)
                .startsAt(LocalDateTime.now().minusDays(1))
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();

        assertThat(entitlement.isUsable(purchase)).isFalse();
        assertThat(entitlement.grantsScope(purchase)).isTrue();
    }

    @Test
    void isUsable_whenFiniteEntitlementHasNoRemainingQuantity_thenReturnFalse() {
        UserEntitlement entitlement = UserEntitlement.builder()
                .remainingQuantity(0)
                .unlimited(false)
                .startsAt(LocalDateTime.now().minusDays(1))
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();

        assertThat(entitlement.isUsable(PurchaseStatus.ACTIVE)).isFalse();
    }

    @Test
    void isUsable_whenUnlimitedEntitlementHasNoRemainingQuantity_thenReturnTrue() {
        UserEntitlement entitlement = UserEntitlement.builder()
                .remainingQuantity(0)
                .unlimited(true)
                .startsAt(LocalDateTime.now().minusDays(1))
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();

        assertThat(entitlement.isUsable(PurchaseStatus.ACTIVE)).isTrue();
    }

    @Test
    void isUsable_whenEntitlementExpiresAtPassed_thenReturnFalse() {
        UserEntitlement entitlement = UserEntitlement.builder()
                .remainingQuantity(5)
                .unlimited(false)
                .startsAt(LocalDateTime.now().minusDays(10))
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .build();

        assertThat(entitlement.isUsable(PurchaseStatus.ACTIVE)).isFalse();
    }
}
