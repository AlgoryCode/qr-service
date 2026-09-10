package com.ael.algoryqrservice.model.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public final class WorkShiftDtos {

    private WorkShiftDtos() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OpenShiftRequest {
        private Long menuId;

        @NotNull
        @Min(0)
        private BigDecimal openingFloat;

        private Set<Long> waiterIds;

        @Size(max = 1000)
        private String note;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CloseShiftRequest {
        @NotNull
        @Min(0)
        private BigDecimal closingCash;

        @Size(max = 1000)
        private String note;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShiftResponse {
        private Long id;
        private Long branchId;
        private Long menuId;
        private Long openedByWaiterId;
        private Long closedByWaiterId;
        private LocalDateTime openedAt;
        private LocalDateTime closedAt;
        private BigDecimal openingFloat;
        private BigDecimal closingCash;
        private String note;
        private String status;
        private Set<Long> waiterIds;
        private BigDecimal revenue;
        private long orderCount;
    }
}
