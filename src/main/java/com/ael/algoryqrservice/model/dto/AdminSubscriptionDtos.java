package com.ael.algoryqrservice.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

public final class AdminSubscriptionDtos {

    private AdminSubscriptionDtos() {
    }

    @Data
    public static class ExtendRequest {
        @Min(1)
        @Max(3650)
        private int days;
    }
}
