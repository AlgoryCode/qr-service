package com.ael.algoryqrservice.model.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BillingPeriodTest {

    @Test
    void intervalMonths_whenMonthly_thenOne() {
        assertThat(BillingPeriod.MONTHLY.intervalMonths()).isEqualTo(1);
    }

    @Test
    void intervalMonths_whenYearly_thenTwelve() {
        assertThat(BillingPeriod.YEARLY.intervalMonths()).isEqualTo(12);
    }

    @Test
    void intervalMonths_whenOneTime_thenZero() {
        assertThat(BillingPeriod.ONE_TIME.intervalMonths()).isZero();
    }

    @Test
    void valueOf_whenOneTime_thenResolve() {
        assertThat(BillingPeriod.valueOf("ONE_TIME")).isEqualTo(BillingPeriod.ONE_TIME);
    }
}
