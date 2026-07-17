package com.ael.algoryqrservice.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSavedCardRequest(
        @Size(max = 255) String alias,
        @NotBlank(message = "Kart üzerindeki isim zorunludur") String cardHolderName,
        @NotBlank(message = "Kart numarası zorunludur")
        @Size(min = 13, max = 19, message = "Kart numarası geçersiz")
        String cardNumber,
        @NotBlank(message = "Son kullanma ayı zorunludur") String expireMonth,
        @NotBlank(message = "Son kullanma yılı zorunludur") String expireYear
) {
}
