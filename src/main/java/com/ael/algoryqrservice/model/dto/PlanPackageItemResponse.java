package com.ael.algoryqrservice.model.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PlanPackageItemResponse {

    private Long id;
    private Long productId;
    private String productCode;
    private String productName;
    private Integer quantity;
    private boolean unlimited;
}
