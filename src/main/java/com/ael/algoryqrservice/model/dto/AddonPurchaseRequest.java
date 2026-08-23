package com.ael.algoryqrservice.model.dto;

import com.ael.algoryqrservice.model.enums.PaymentMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AddonPurchaseRequest {

    @NotBlank(message = "Urun kodu zorunludur")
    private String productCode;

    @Min(1)
    @Max(20)
    private int quantity = 1;

    private PaymentMode paymentMode = PaymentMode.CHECKOUT_FORM;

    private Long billingAddressId;

    private Long paymentMethodId;

    @Valid
    private BillingAddressDtos.Request inlineBillingAddress;

    @Valid
    private AddressDto billingAddress;

    private String identityNumber;

    @AssertTrue(message = "Fatura adresi seçimi geçersiz; billingAddressId gönderilmelidir")
    public boolean isBillingSelectionValid() {
        int selections = billingAddressId != null ? 1 : 0;
        selections += inlineBillingAddress != null ? 1 : 0;
        selections += billingAddress != null ? 1 : 0;
        return selections == 1;
    }

    public String resolvedProductCode() {
        return productCode == null ? "" : productCode.trim().toUpperCase();
    }

    public int resolvedQuantity() {
        return quantity < 1 ? 1 : quantity;
    }
}
