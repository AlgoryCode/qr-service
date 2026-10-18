package com.ael.algoryqrservice.staffservice.dto;

import com.ael.algoryqrservice.qrservice.model.enums.WaiterCommissionRecordType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class StaffCommissionDtos {

    private StaffCommissionDtos() {
    }

    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CommissionRecordResponse {
        private Long id;
        private Long billId;
        private Long orderId;
        private WaiterCommissionRecordType recordType;
        private BigDecimal baseAmount;
        private BigDecimal commissionValueSnapshot;
        private BigDecimal amount;
        private String currency;
        private String tableName;
        private LocalDateTime createdAt;
    }

    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TodayCommissionSummary {
        private BigDecimal totalAmount;
        private String currency;
        private BigDecimal percentOrderTotal;
        private BigDecimal fixedTableCloseTotal;
        private BigDecimal fixedItemAddTotal;
        private int recordCount;
        private List<CommissionRecordResponse> records;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CommissionHistoryResponse {
        private List<CommissionRecordResponse> records;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
    }
}
