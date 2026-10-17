package com.ael.algoryqrservice.paymentservice.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenamePaymentMethodRequest(@NotBlank @Size(max = 255) String alias) {
}
