package com.ael.algoryqrservice.model.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class MenuFixedExpenseDtos {

    private MenuFixedExpenseDtos() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long id;
        private Long menuId;
        private String title;
        private BigDecimal dailyAmount;
        private boolean active;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        @NotBlank
        @Size(max = 200)
        private String title;

        @DecimalMin(value = "0.01")
        private BigDecimal dailyAmount;

        private Boolean active;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRequest {
        @Size(max = 200)
        private String title;

        @DecimalMin(value = "0.01")
        private BigDecimal dailyAmount;

        private Boolean active;
    }
}
