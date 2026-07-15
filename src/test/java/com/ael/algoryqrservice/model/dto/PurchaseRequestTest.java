package com.ael.algoryqrservice.model.dto;

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
    void isPaymentPlanValid_whenUnsupportedInstallmentCount_thenReject() {
        PurchaseRequest request = new PurchaseRequest();
        request.setPaymentMode(PaymentMode.THREE_DS);
        request.setInstallmentCount(4);

        assertThat(validator.validateProperty(request, "paymentPlanValid")).isNotEmpty();
    }

    @Test
    void isPaymentPlanValid_whenDirectInstallmentCountAllowed_thenAccept() {
        PurchaseRequest request = new PurchaseRequest();
        request.setPaymentMode(PaymentMode.DIRECT);
        request.setInstallmentCount(6);

        assertThat(request.isPaymentPlanValid()).isTrue();
    }

    @Test
    void isPaymentPlanValid_whenInstallmentCountUnsupported_thenReject() {
        PurchaseRequest request = new PurchaseRequest();
        request.setPaymentMode(PaymentMode.THREE_DS);
        request.setInstallmentCount(5);

        assertThat(request.isPaymentPlanValid()).isFalse();
    }
}
