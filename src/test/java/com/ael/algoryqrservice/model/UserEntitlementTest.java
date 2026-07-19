package com.ael.algoryqrservice.model;

import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class UserEntitlementTest {

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
