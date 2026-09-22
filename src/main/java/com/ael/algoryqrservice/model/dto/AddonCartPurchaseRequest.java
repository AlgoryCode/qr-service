package com.ael.algoryqrservice.model.dto;

import com.ael.algoryqrservice.model.enums.PaymentMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class AddonCartPurchaseRequest {

    @NotEmpty(message = "Sepette en az bir modul olmalidir")
    @Valid
    private List<AddonCartLineRequest> modules;

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
}
