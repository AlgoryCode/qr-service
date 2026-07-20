package com.ael.algoryqrservice.model.dto;

import com.ael.algoryqrservice.model.enums.PlanChangeTiming;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class PlanChangeOptionResponse {
    private PlanChangeTiming timing;
    private BigDecimal chargeNow;
    private BigDecimal refundNow;
    private BigDecimal chargeAtEffective;
    private LocalDateTime effectiveAt;
    private String entitlementsPolicy;
}
