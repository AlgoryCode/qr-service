package com.ael.algoryqrservice.model.dto;

import com.ael.algoryqrservice.model.enums.BillingPeriod;
import com.ael.algoryqrservice.model.enums.PaymentMode;
import com.ael.algoryqrservice.model.enums.PaymentStyle;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PurchaseRequest {

    @NotNull(message = "Paket id zorunludur")
    private Long packageId;

    @NotNull(message = "Ödeme tipi zorunludur")
    private PaymentMode paymentMode = PaymentMode.THREE_DS;

    @NotNull(message = "Faturalama periyodu zorunludur")
    private BillingPeriod billingPeriod;

    private String identityNumber;

    private PaymentStyle paymentStyle;

    private Long billingAddressId;

    private Long paymentMethodId;

    @Valid
    private BillingAddressDtos.Request inlineBillingAddress;

    @Valid
    private PaymentCardDto paymentCard;

    @Valid
    private AddressDto billingAddress;

    @Valid
    private AddressDto shippingAddress;

    @AssertTrue(message = "Kart veya kayıtlı ödeme yöntemi zorunludur")
    public boolean isPaymentInstrumentValid() {
        return paymentMethodId != null || paymentCard != null;
    }

    @AssertTrue(message = "Ödeme planı geçersiz")
    public boolean isPaymentPlanValid() {
        if (billingPeriod == null) {
            return false;
        }
        PaymentStyle style = resolvedPaymentStyle();
        return style == PaymentStyle.SUBSCRIPTION
                && (billingPeriod == BillingPeriod.MONTHLY || billingPeriod == BillingPeriod.YEARLY);
    }

    @AssertTrue(message = "Fatura adresi seçimi geçersiz")
    public boolean isBillingSelectionValid() {
        int selections = billingAddressId != null ? 1 : 0;
        selections += inlineBillingAddress != null ? 1 : 0;
        selections += billingAddress != null ? 1 : 0;
        return selections == 1;
    }

    public PaymentStyle resolvedPaymentStyle() {
        return PaymentStyle.SUBSCRIPTION;
    }

    public BillingPeriod resolvedBillingPeriod() {
        return billingPeriod;
    }
}
