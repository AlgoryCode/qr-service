package com.ael.algoryqrservice.paymentservice.presentation.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardVerificationInitRequest {

    @NotBlank
    private String serviceName;

    private String sourceReferenceId;

    @NotBlank
    private String conversationId;

    @NotBlank
    private String locale;

    @NotBlank
    private String currency;

    @NotNull
    @Valid
    private BuyerRequest buyer;

    @NotNull
    @Valid
    private AddressRequest shippingAddress;

    @NotNull
    @Valid
    private AddressRequest billingAddress;
}
