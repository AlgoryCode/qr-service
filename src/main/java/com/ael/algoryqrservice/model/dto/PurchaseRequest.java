package com.ael.algoryqrservice.model.dto;

import com.ael.algoryqrservice.model.enums.PaymentMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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

    @NotBlank(message = "TC kimlik numarası zorunludur")
    private String identityNumber;

    @NotNull(message = "Kart bilgileri zorunludur")
    @Valid
    private PaymentCardDto paymentCard;

    @NotNull(message = "Fatura adresi zorunludur")
    @Valid
    private AddressDto billingAddress;

    @Valid
    private AddressDto shippingAddress;

    @AssertTrue(message = "Taksitli tahsilat DIRECT ödeme tipiyle ve 2, 3, 6, 9 veya 12 taksitle yapılmalıdır")
    public boolean isPaymentPlanValid() {
        if (installmentCount == null) {
            return true;
        }
        boolean allowedCount = installmentCount == 1
                || installmentCount == 2
                || installmentCount == 3
                || installmentCount == 6
                || installmentCount == 9
                || installmentCount == 12;
        return allowedCount && (installmentCount == 1 || paymentMode == PaymentMode.DIRECT);
    }
}
