package com.ael.algoryqrservice.model.dto;

import com.ael.algoryqrservice.model.enums.PackageCode;
import com.ael.algoryqrservice.model.enums.PaymentMode;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class PurchaseSummaryResponse {

    private Long purchaseId;
    private Long userId;
    private Long packageId;
    private PackageCode packageCode;
    private String packageName;
    private BigDecimal price;
    private String currency;
    private PurchaseStatus status;
    private PaymentMode paymentMode;
    private Integer installmentCount;
    private LocalDateTime startsAt;
    private LocalDateTime expiresAt;
    private LocalDateTime purchasedAt;
    private boolean expired;
    private boolean usable;
    private List<UserEntitlementResponse> products;
    private List<PurchaseFulfillmentResponse> installments;
}
