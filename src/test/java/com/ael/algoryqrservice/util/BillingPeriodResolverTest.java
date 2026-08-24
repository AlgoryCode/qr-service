package com.ael.algoryqrservice.util;

import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.enums.BillingPeriod;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BillingPeriodResolverTest {

    @Test
    void resolve_whenBillingPeriodPresent_thenReturnIt() {
        Purchase purchase = Purchase.builder().billingPeriod(BillingPeriod.YEARLY).build();

        assertThat(BillingPeriodResolver.resolve(purchase)).isEqualTo(BillingPeriod.YEARLY);
    }

    @Test
    void resolve_whenIntervalMonthsAtLeastTwelve_thenReturnYearly() {
        Purchase purchase = Purchase.builder().billingIntervalMonths(12).build();

        assertThat(BillingPeriodResolver.resolve(purchase)).isEqualTo(BillingPeriod.YEARLY);
    }

    @Test
    void resolve_whenBillingFieldsMissing_thenReturnMonthly() {
        Purchase purchase = Purchase.builder().build();

        assertThat(BillingPeriodResolver.resolve(purchase)).isEqualTo(BillingPeriod.MONTHLY);
    }
}
