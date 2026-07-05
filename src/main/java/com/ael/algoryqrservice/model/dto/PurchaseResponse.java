package com.ael.algoryqrservice.model.dto;

import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class PurchaseResponse {

    private Long id;
    private Long userId;
    private Long packageId;
    private String packageCode;
    private String packageName;
    private BigDecimal price;
    private String currency;
    private PurchaseStatus status;
    private LocalDateTime startsAt;
    private LocalDateTime expiresAt;
    private LocalDateTime purchasedAt;
    private boolean expired;
    private boolean usable;
}
