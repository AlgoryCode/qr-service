package com.ael.algoryqrservice.model.dto;

import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

public final class AdminSubscriptionDtos {

    private AdminSubscriptionDtos() {
    }

    @Data
    public static class PurchaseUpdateRequest {
        private PurchaseStatus status;
    }

    @Data
    public static class SubscriptionUpdateRequest {
        private String status;

        @Min(1)
        @Max(3650)
        private Integer days;
    }
}
