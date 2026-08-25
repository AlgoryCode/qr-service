package com.ael.algoryqrservice.model.dto;

import com.ael.algoryqrservice.model.enums.FulfillmentDetailSource;
import com.ael.algoryqrservice.model.enums.ProductType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class FulfillmentDetailResponse {

    private Long id;
    private Long fulfillmentId;
    private String featureCode;
    private String scopeCode;
    private ProductType productTypeId;
    private FulfillmentDetailSource source;
    private Integer quantity;
    private boolean unlimited;
    private Integer usedQuantity;
    private Integer remainingQuantity;
    private LocalDateTime startsAt;
    private LocalDateTime expiresAt;
}
