package com.ael.algoryqrservice.model.dto;

import com.ael.algoryqrservice.model.enums.PaymentMode;
import com.ael.algoryqrservice.model.enums.PaymentStyle;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PurchaseRequest {

    @NotNull(message = "Paket id zorunludur")
    private Long packageId;

    @NotNull(message = "Ödeme tipi zorunludur")
    private PaymentMode paymentMode = PaymentMode.THREE_DS;

    @NotNull(message = "Taksit sayısı zorunludur")
    @Min(value = 1, message = "Taksit sayısı en az 1 olmalıdır")
    @Max(value = 12, message = "Taksit sayısı en fazla 12 olabilir")
    private Integer installmentCount = 1;

    private String identityNumber;

    private PaymentStyle paymentStyle;

    private Long billingAddressId;

    private Long paymentMethodId;

    private Integer bankInstallmentCount;

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
        Integer count = resolvedInstallmentCount();
        if (count == null) {
            return true;
        }
        boolean allowedCount = count == 1
                || count == 2
                || count == 3
                || count == 6
                || count == 9
                || count == 12;
        if (!allowedCount) {
            return false;
        }
        PaymentStyle style = resolvedPaymentStyle();
        return switch (style) {
            case ONE_TIME -> count == 1;
            case BANK_INSTALLMENT -> count > 1;
            case SUBSCRIPTION -> true;
        };
    }

    @AssertTrue(message = "Fatura adresi seçimi geçersiz")
    public boolean isBillingSelectionValid() {
        int selections = billingAddressId != null ? 1 : 0;
        selections += inlineBillingAddress != null ? 1 : 0;
        selections += billingAddress != null ? 1 : 0;
        return selections == 1;
    }

    public PaymentStyle resolvedPaymentStyle() {
        if (paymentStyle != null) {
            return paymentStyle;
        }
        Integer count = resolvedInstallmentCount();
        return count != null && count > 1
                ? PaymentStyle.BANK_INSTALLMENT
                : PaymentStyle.ONE_TIME;
    }

    public Integer resolvedInstallmentCount() {
        if (bankInstallmentCount != null) {
            return bankInstallmentCount;
        }
        return installmentCount;
    }
}
