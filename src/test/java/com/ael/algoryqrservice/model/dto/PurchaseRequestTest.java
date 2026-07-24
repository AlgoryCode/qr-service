package com.ael.algoryqrservice.model.dto;

import com.ael.algoryqrservice.model.enums.BillingPeriod;
import com.ael.algoryqrservice.model.enums.PaymentMode;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseRequestTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void isPaymentPlanValid_whenBillingPeriodMissing_thenReject() {
        PurchaseRequest request = new PurchaseRequest();
        request.setPaymentMode(PaymentMode.THREE_DS);

        assertThat(request.isPaymentPlanValid()).isFalse();
    }

    @Test
    void isPaymentPlanValid_whenMonthly_thenAccept() {
        PurchaseRequest request = new PurchaseRequest();
        request.setPaymentMode(PaymentMode.THREE_DS);
        request.setBillingPeriod(BillingPeriod.MONTHLY);

        assertThat(request.isPaymentPlanValid()).isTrue();
        assertThat(request.resolvedPaymentStyle().name()).isEqualTo("SUBSCRIPTION");
    }

    @Test
    void isPaymentPlanValid_whenYearly_thenAccept() {
        PurchaseRequest request = new PurchaseRequest();
        request.setPaymentMode(PaymentMode.THREE_DS);
        request.setBillingPeriod(BillingPeriod.YEARLY);

        assertThat(request.isPaymentPlanValid()).isTrue();
    }
}
