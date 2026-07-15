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
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();

        assertThat(entitlement.isUsable(PurchaseStatus.ACTIVE)).isFalse();
    }

    @Test
    void isUsable_whenUnlimitedEntitlementHasNoRemainingQuantity_thenReturnTrue() {
        UserEntitlement entitlement = UserEntitlement.builder()
                .remainingQuantity(0)
                .unlimited(true)
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();

        assertThat(entitlement.isUsable(PurchaseStatus.ACTIVE)).isTrue();
    }
}
