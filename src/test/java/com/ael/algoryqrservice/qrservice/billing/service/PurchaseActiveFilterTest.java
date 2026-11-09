package com.ael.algoryqrservice.qrservice.billing.service;

import com.ael.algoryqrservice.qrservice.model.Purchase;
import com.ael.algoryqrservice.qrservice.model.enums.PurchaseStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

class PurchaseActiveFilterTest {

    private Purchase purchase(boolean usable, boolean expired, PurchaseStatus status) {
        Purchase purchase = mock(Purchase.class);
        lenient().when(purchase.isUsable()).thenReturn(usable);
        lenient().when(purchase.isEffectivelyExpired()).thenReturn(expired);
        lenient().when(purchase.getStatus()).thenReturn(status);
        return purchase;
    }

    @Test
    void keepsAUsablePurchase() {
        assertThat(PurchaseService.canStillBeActive(purchase(true, false, PurchaseStatus.ACTIVE))).isTrue();
    }

    @Test
    void dropsPurchasesThatCanNeverBeTheCurrentPackage() {
        assertThat(PurchaseService.canStillBeActive(purchase(false, false, PurchaseStatus.FAILED))).isFalse();
        assertThat(PurchaseService.canStillBeActive(purchase(true, true, PurchaseStatus.ACTIVE))).isFalse();
        assertThat(PurchaseService.canStillBeActive(purchase(true, false, PurchaseStatus.EXPIRED))).isFalse();
        assertThat(PurchaseService.canStillBeActive(purchase(true, false, PurchaseStatus.CANCELLED))).isFalse();
    }
}
