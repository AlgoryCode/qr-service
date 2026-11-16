package com.ael.algoryqrservice.qrservice.billing.service;

import com.ael.algoryqrservice.fulfillmentservice.domain.model.PackageStatus;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.SubscriptionEvent;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.SubscriptionEventType;
import com.ael.algoryqrservice.qrservice.billing.service.entitlement.AddonEntitlementService;
import com.ael.algoryqrservice.qrservice.client.PaymentServiceClient;
import com.ael.algoryqrservice.qrservice.model.Purchase;
import com.ael.algoryqrservice.qrservice.model.enums.PaymentStyle;
import com.ael.algoryqrservice.qrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.qrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.qrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.qrservice.util.AppTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecurringAddonBillingTest {

    private static final long MERCHANT = 7L;

    private PurchaseRepository purchases;
    private PaymentServiceClient payments;
    private AddonEntitlementService addonEntitlements;
    private RecurringAddonBilling billing;

    @BeforeEach
    void setUp() {
        purchases = mock(PurchaseRepository.class);
        payments = mock(PaymentServiceClient.class);
        addonEntitlements = mock(AddonEntitlementService.class);
        billing = new RecurringAddonBilling(purchases, payments, addonEntitlements);
        when(payments.switchSubscriptionPlan(anyLong(), anyString(), anyLong(), any(), anyMap())).thenReturn(true);
    }

    @Test
    void chargesOnlyTheDaysLeftInTheHostPeriod() {
        Purchase host = host(LocalDateTime.of(2026, 12, 16, 0, 0));

        BigDecimal share = RecurringAddonBilling.prorate(
                new BigDecimal("100.00"), host, LocalDateTime.of(2026, 12, 1, 0, 0));

        assertThat(share).isEqualByComparingTo("50.00");
    }

    @Test
    void partialDayCountsAsAWholeDayAndNeverExceedsTheFullPrice() {
        Purchase host = host(LocalDateTime.of(2026, 12, 16, 0, 0));

        assertThat(RecurringAddonBilling.prorate(new BigDecimal("30.00"), host, LocalDateTime.of(2026, 12, 15, 23, 0)))
                .isEqualByComparingTo("1.00");
        assertThat(RecurringAddonBilling.prorate(new BigDecimal("30.00"), host, LocalDateTime.of(2026, 10, 1, 0, 0)))
                .isEqualByComparingTo("30.00");
    }

    @Test
    void renewalAmountAddsLaterModulesToThePackagePrice() {
        Purchase host = host(LocalDateTime.of(2026, 12, 16, 0, 0));
        host.setRecurringPrice(new BigDecimal("499.00"));
        host.setAddonRecurringAmount(new BigDecimal("150.00"));

        assertThat(RecurringAddonBilling.renewalAmount(host)).isEqualByComparingTo("649.00");
    }

    @Test
    void syncPutsActiveRecurringModulesOnTheSubscription() {
        Purchase host = host(AppTime.nowLocal().plusDays(10));
        host.setRecurringPrice(new BigDecimal("499.00"));
        Purchase recurring = addon(10L, PurchaseStatus.ACTIVE, new BigDecimal("100.00"), host.getExpiresAt());
        Purchase cancelled = addon(11L, PurchaseStatus.CANCELLED, new BigDecimal("80.00"), host.getExpiresAt());
        Purchase oneTime = addon(12L, PurchaseStatus.ACTIVE, null, host.getExpiresAt());
        when(purchases.findByMerchantIdAndStatus(MERCHANT, PurchaseStatus.ACTIVE)).thenReturn(List.of(host, recurring, oneTime));
        when(purchases.findByMerchantIdOrderByPurchasedAtDesc(MERCHANT)).thenReturn(List.of(host, recurring, cancelled, oneTime));

        billing.sync(MERCHANT);

        ArgumentCaptor<BigDecimal> amount = ArgumentCaptor.forClass(BigDecimal.class);
        verify(payments).switchSubscriptionPlan(eq(MERCHANT), eq("sub-1"), eq(1L), amount.capture(), anyMap());
        assertThat(amount.getValue()).isEqualByComparingTo("599.00");
        assertThat(host.getAddonRecurringAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    void syncLeavesOneTimePackagesAlone() {
        Purchase host = host(AppTime.nowLocal().plusDays(10));
        host.setPaymentStyle(PaymentStyle.ONE_TIME);
        host.setSubscriptionId(null);
        when(purchases.findByMerchantIdAndStatus(MERCHANT, PurchaseStatus.ACTIVE)).thenReturn(List.of(host));

        billing.sync(MERCHANT);

        verify(payments, never()).switchSubscriptionPlan(anyLong(), anyString(), anyLong(), any(), anyMap());
    }

    @Test
    void renewalMovesRecurringModulesToTheNewPeriodEnd() {
        LocalDateTime oldEnd = AppTime.nowLocal().minusHours(2);
        LocalDateTime newEnd = oldEnd.plusMonths(1);
        Purchase lapsed = addon(10L, PurchaseStatus.EXPIRED, new BigDecimal("100.00"), oldEnd);
        Purchase longGone = addon(11L, PurchaseStatus.EXPIRED, new BigDecimal("100.00"), oldEnd.minusMonths(3));
        when(purchases.findByMerchantIdOrderByPurchasedAtDesc(MERCHANT)).thenReturn(List.of(lapsed, longGone));

        billing.onSubscriptionEvent(new SubscriptionEvent(MERCHANT, SubscriptionEventType.PACKAGE_RENEWED, "PRO",
                null, PackageStatus.ACTIVE, newEnd.atZone(AppTime.ZONE).toInstant(), null, "system", null,
                java.time.Instant.now()));

        assertThat(lapsed.getStatus()).isEqualTo(PurchaseStatus.ACTIVE);
        assertThat(lapsed.getExpiresAt()).isEqualTo(newEnd);
        verify(addonEntitlements).extend(lapsed);
        verify(addonEntitlements, never()).extend(longGone);
    }

    private static Purchase host(LocalDateTime expiresAt) {
        Purchase host = new Purchase();
        host.setId(1L);
        host.setMerchantId(MERCHANT);
        host.setPackageId(3L);
        host.setPurchaseType(PurchaseType.PAID);
        host.setStatus(PurchaseStatus.ACTIVE);
        host.setPaymentStyle(PaymentStyle.SUBSCRIPTION);
        host.setSubscriptionId("sub-1");
        host.setBillingIntervalMonths(1);
        host.setPrice(new BigDecimal("499.00"));
        host.setExpiresAt(expiresAt);
        return host;
    }

    private static Purchase addon(Long id, PurchaseStatus status, BigDecimal recurringPrice, LocalDateTime expiresAt) {
        Purchase addon = new Purchase();
        addon.setId(id);
        addon.setMerchantId(MERCHANT);
        addon.setPurchaseType(PurchaseType.ADD_ON);
        addon.setStatus(status);
        addon.setRecurringPrice(recurringPrice);
        addon.setExpiresAt(expiresAt);
        return addon;
    }
}
