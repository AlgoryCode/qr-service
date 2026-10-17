package com.ael.algoryqrservice.paymentservice.presentation.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCardRequest {

    @NotBlank
    @ToString.Exclude
    private String cardHolderName;

    @NotBlank
    @ToString.Exclude
    private String cardNumber;

    @NotBlank
    @ToString.Exclude
    private String expireMonth;

    @NotBlank
    @ToString.Exclude
    private String expireYear;

    @NotBlank
    @ToString.Exclude
    private String cvc;

    private Integer registerCard;
}
