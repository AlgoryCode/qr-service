package com.ael.algoryqrservice.paymentservice.presentation.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePaymentMethodRequest(
        @NotBlank @Size(max = 255) String alias,
        @NotBlank @Email String email,
        @NotBlank String cardHolderName,
        @NotBlank @Size(min = 13, max = 19) String cardNumber,
        @NotBlank String expireMonth,
        @NotBlank String expireYear
) {

    @Override
    public String toString() {
        return "CreatePaymentMethodRequest[alias=" + alias + ", email=" + email + ", card=REDACTED]";
    }
}
