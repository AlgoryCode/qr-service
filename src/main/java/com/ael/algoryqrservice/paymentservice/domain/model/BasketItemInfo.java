package com.ael.algoryqrservice.paymentservice.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BasketItemInfo {

    private String id;
    private String name;
    private String category1;
    private String category2;
    private String itemType;
    private BigDecimal price;
}
