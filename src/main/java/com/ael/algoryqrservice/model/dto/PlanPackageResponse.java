package com.ael.algoryqrservice.model.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class PlanPackageResponse {

    private Long id;
    private String code;
    private String name;
    private String description;
    private BigDecimal price;
    private String currency;
    private boolean active;
    private Integer validityDays;
    private List<PlanPackageItemResponse> items;
    private LocalDateTime createdAt;
}
