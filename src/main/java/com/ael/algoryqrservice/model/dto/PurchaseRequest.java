package com.ael.algoryqrservice.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PurchaseRequest {

    @NotNull(message = "Paket id zorunludur")
    private Long packageId;

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
}
