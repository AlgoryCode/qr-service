package com.ael.algoryqrservice.model.dto;

import com.ael.algoryqrservice.model.enums.PlanChangeDirection;
import com.ael.algoryqrservice.model.enums.PlanChangeStatus;
import com.ael.algoryqrservice.model.enums.PlanChangeTiming;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class PlanChangeResponse {
    private Long id;
    private Long userId;
    private Long fromPurchaseId;
    private Long fromPackageId;
    private Long toPackageId;
    private String fromPackageCode;
    private String toPackageCode;
    private String fromPackageName;
    private String toPackageName;
    private PlanChangeDirection direction;
    private PlanChangeTiming timing;
    private PlanChangeStatus status;
    private BigDecimal chargeAmount;
    private BigDecimal refundAmount;
    private String currency;
    private Long paymentMethodId;
    private LocalDateTime effectiveAt;
    private Long resultingPurchaseId;
    private boolean warningAck;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
