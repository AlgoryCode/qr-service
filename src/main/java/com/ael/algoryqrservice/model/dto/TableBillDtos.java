package com.ael.algoryqrservice.model.dto;

import com.ael.algoryqrservice.model.enums.TableBillPaymentMethod;
import com.ael.algoryqrservice.model.enums.TableBillStatus;
import com.ael.algoryqrservice.model.enums.WaiterCommissionRecordType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
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

public final class TableBillDtos {

    private TableBillDtos() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BillItemResponse {
        private Long id;
        private Long productId;
        private String productName;
        private BigDecimal unitPrice;
        private int quantity;
        private BigDecimal lineTotal;
        private String note;
        private Long sourceOrderId;
        private Long addedByWaiterId;
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BillResponse {
        private Long id;
        private Long menuId;
        private Long tableId;
        private String tableName;
        private TableBillStatus status;
        private Long openedByWaiterId;
        private Long closedByWaiterId;
        private LocalDateTime openedAt;
        private LocalDateTime closedAt;
        private TableBillPaymentMethod paymentMethod;
        private BigDecimal totalAmount;
        private String currency;
        private int itemCount;
        private List<BillItemResponse> items;
        private BigDecimal fixedCommissionAmount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CloseBillRequest {
        @NotNull
        private TableBillPaymentMethod paymentMethod;

        private Boolean tipReceived;

        @DecimalMin(value = "0.01")
        private BigDecimal tipAmount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateBillItemsRequest {
        @NotNull
        @Valid
        @Size(min = 1)
        private List<MenuOrderDtos.CartItemRequest> items;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateBillItemQuantityRequest {
        @NotNull
        @Min(0)
        private Integer quantity;
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
