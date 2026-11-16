package com.ael.algoryqrservice.paymentservice.application.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionRenewalMathTest {

    private static final LocalDateTime DUE = LocalDateTime.of(2026, 10, 15, 9, 0);

    @Test
    void firstPaymentStartsTheCycleFromNow() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 3, 12, 0);
        assertThat(SubscriptionService.nextChargeAfterPayment(DUE, true, now, 1)).isEqualTo(now.plusMonths(1));
    }

    @Test
    void lateRenewalKeepsTheBillingDay() {
        LocalDateTime paidLate = DUE.plusDays(4);
        assertThat(SubscriptionService.nextChargeAfterPayment(DUE, false, paidLate, 1))
                .isEqualTo(LocalDateTime.of(2026, 11, 15, 9, 0));
    }

    @Test
    void renewalMoreThanAPeriodLateRestartsFromNow() {
        LocalDateTime veryLate = DUE.plusMonths(1).plusDays(2);
        assertThat(SubscriptionService.nextChargeAfterPayment(DUE, false, veryLate, 1))
                .isEqualTo(veryLate.plusMonths(1));
    }

    @Test
    void renewalChargesTheRecurringPriceNotTheDiscountedFirstPayment() {
        assertThat(SubscriptionService.renewalAmount(Map.of("recurringPrice", "499.00"), new BigDecimal("249.50")))
                .isEqualByComparingTo("499.00");
        assertThat(SubscriptionService.renewalAmount(Map.of("recurringPrice", 799), BigDecimal.ONE))
                .isEqualByComparingTo("799");
    }

    @Test
    void missingOrBadRecurringPriceFallsBackToTheFirstPayment() {
        assertThat(SubscriptionService.renewalAmount(null, new BigDecimal("99.90"))).isEqualByComparingTo("99.90");
        assertThat(SubscriptionService.renewalAmount(Map.of("recurringPrice", "abc"), new BigDecimal("99.90")))
                .isEqualByComparingTo("99.90");
        assertThat(SubscriptionService.renewalAmount(Map.of("recurringPrice", "0"), new BigDecimal("99.90")))
                .isEqualByComparingTo("99.90");
    }
}
