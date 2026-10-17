package com.ael.algoryqrservice.paymentservice.presentation.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BasketItemRequest {

    @NotBlank
    private String id;

    @NotBlank
    private String name;

    @NotBlank
    private String category1;

    private String category2;

    @NotBlank
    private String itemType;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal price;
}
