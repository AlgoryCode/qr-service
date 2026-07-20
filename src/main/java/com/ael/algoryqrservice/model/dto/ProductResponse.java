package com.ael.algoryqrservice.model.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class ProductResponse {

    private Long id;
    private String code;
    private String name;
    private String description;
    private String scopeCode;
    private BigDecimal unitPrice;
    private BigDecimal vatRate;
    private boolean countable;
    private boolean consumable;
    private boolean active;
    private LocalDateTime createdAt;
}
