package com.ael.algoryqrservice.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PaymentCardDto {

    @NotBlank(message = "Kart sahibi adı zorunludur")
    private String cardHolderName;

    @NotBlank(message = "Kart numarası zorunludur")
    private String cardNumber;

    @NotBlank(message = "Son kullanma ayı zorunludur")
    private String expireMonth;

    @NotBlank(message = "Son kullanma yılı zorunludur")
    private String expireYear;

    @NotBlank(message = "CVC zorunludur")
    private String cvc;

    private Integer registerCard = 0;
}
