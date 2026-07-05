package com.ael.algoryqrservice.model.dto;

import com.ael.algoryqrservice.model.enums.ProductCode;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PlanPackageItemResponse {

    private Long id;
    private Long productId;
    private ProductCode productCode;
    private String productName;
    private Integer quantity;
}
