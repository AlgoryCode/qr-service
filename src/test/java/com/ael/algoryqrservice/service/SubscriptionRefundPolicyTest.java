package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.config.BillingRefundProperties;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.enums.BillingPeriod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionRefundPolicyTest {

    private SubscriptionRefundPolicy policy;

    @BeforeEach
    void setUp() {
        BillingRefundProperties properties = new BillingRefundProperties();
        properties.setMonthlyCoolingDays(7);
        properties.setYearlyCoolingDays(14);
        policy = new SubscriptionRefundPolicy(properties);
    }

    @Test
    void isRefundEligible_whenWithinMonthlyWindow_thenTrue() {
        Purchase purchase = Purchase.builder()
                .billingPeriod(BillingPeriod.MONTHLY)
                .currentPeriodPaidAt(LocalDateTime.now().minusDays(3))
                .build();

        assertThat(policy.isRefundEligible(purchase, LocalDateTime.now())).isTrue();
        assertThat(policy.refundEligibleUntil(purchase))
                .isEqualTo(purchase.getCurrentPeriodPaidAt().plusDays(7));
    }

    @Test
    void isRefundEligible_whenOutsideMonthlyWindow_thenFalse() {
        Purchase purchase = Purchase.builder()
                .billingPeriod(BillingPeriod.MONTHLY)
                .currentPeriodPaidAt(LocalDateTime.now().minusDays(8))
                .build();

        assertThat(policy.isRefundEligible(purchase, LocalDateTime.now())).isFalse();
    }

    @Test
    void isRefundEligible_whenYearlyWithin14Days_thenTrue() {
        Purchase purchase = Purchase.builder()
                .billingPeriod(BillingPeriod.YEARLY)
                .currentPeriodPaidAt(LocalDateTime.now().minusDays(10))
                .build();

        assertThat(policy.isRefundEligible(purchase, LocalDateTime.now())).isTrue();
    }

    @Test
    void isRefundEligible_whenAlreadyRefunded_thenFalse() {
        Purchase purchase = Purchase.builder()
                .billingPeriod(BillingPeriod.MONTHLY)
                .currentPeriodPaidAt(LocalDateTime.now().minusDays(1))
                .refundedAt(LocalDateTime.now().minusHours(1))
                .build();

        assertThat(policy.isRefundEligible(purchase, LocalDateTime.now())).isFalse();
    }

    @Test
    void resolvePeriodPaidAt_whenMissingCurrent_thenFallbackStartsAt() {
        LocalDateTime startsAt = LocalDateTime.now().minusDays(2);
        Purchase purchase = Purchase.builder()
                .startsAt(startsAt)
                .purchasedAt(LocalDateTime.now().minusDays(5))
                .build();

        assertThat(policy.resolvePeriodPaidAt(purchase)).isEqualTo(startsAt);
    }
}
