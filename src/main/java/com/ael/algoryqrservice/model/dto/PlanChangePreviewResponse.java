package com.ael.algoryqrservice.model.dto;

import com.ael.algoryqrservice.model.enums.PlanChangeDirection;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class PlanChangePreviewResponse {
    private Long fromPurchaseId;
    private PlanChangePackageSummary fromPackage;
    private PlanChangePackageSummary toPackage;
    private PlanChangeDirection direction;
    private LocalDateTime currentExpiresAt;
    private List<PlanChangeOptionResponse> options;
    private List<String> warnings;
    private boolean hasScheduledChange;
}
