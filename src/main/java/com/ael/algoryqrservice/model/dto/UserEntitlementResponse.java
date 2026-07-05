package com.ael.algoryqrservice.model.dto;

import com.ael.algoryqrservice.model.enums.ProductCode;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserEntitlementResponse {

    private Long id;
    private Long productId;
    private ProductCode productCode;
    private String productName;
    private Long purchaseId;
    private Integer totalQuantity;
    private Integer remainingQuantity;
    private Integer usedQuantity;
    private LocalDateTime startsAt;
    private LocalDateTime expiresAt;
    private PurchaseStatus purchaseStatus;
    private boolean expired;
    private boolean usable;
    private LocalDateTime createdAt;
}
