package com.ael.algoryqrservice.billservice.dto;

import com.ael.algoryqrservice.billservice.infrastructure.persistence.entity.enums.BillAdjustmentType;
import com.ael.algoryqrservice.billservice.infrastructure.persistence.entity.enums.TableBillPaymentMethod;
import com.ael.algoryqrservice.billservice.infrastructure.persistence.entity.enums.TableBillStatus;
import com.ael.algoryqrservice.qrservice.order.dto.MenuOrderDtos;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
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
        private int paidQuantity;
        private int unpaidQuantity;
        private BigDecimal paidAmount;
        private BigDecimal unpaidAmount;
        private BigDecimal lineTotal;
        private String note;
        private Long sourceOrderId;
        private Long addedByStaffId;
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BillPaymentResponse {
        private Long id;
        private BigDecimal amount;
        private TableBillPaymentMethod paymentMethod;
        private LocalDateTime paidAt;
        private Integer splitShareNumber;
        private Integer splitPersonCount;
        private boolean tip;
        private String itemSummary;
        private Long billItemId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SplitSharePreview {
        private int shareNumber;
        private BigDecimal amount;
        private boolean paid;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SplitPreviewResponse {
        private int personCount;
        private BigDecimal remainingTotal;
        private List<SplitSharePreview> shares;
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
        private Long openedByStaffId;
        private Long closedByStaffId;
        private LocalDateTime openedAt;
        private LocalDateTime closedAt;
        private TableBillPaymentMethod paymentMethod;
        private BigDecimal totalAmount;
        private BigDecimal paidTotal;
        private BigDecimal remainingTotal;
        private String currency;
        private int itemCount;
        private List<BillItemResponse> items;
        private BigDecimal fixedCommissionAmount;
        private Integer splitPersonCount;
        private List<BillPaymentResponse> payments;
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
    public static class PayBillItemLine {
        @NotNull
        private Long itemId;

        @NotNull
        @Min(1)
        private Integer quantityToPay;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PayBillItemsRequest {
        @NotNull
        private TableBillPaymentMethod paymentMethod;

        @NotNull
        @Valid
        @Size(min = 1)
        private List<PayBillItemLine> items;

        private Boolean tipReceived;

        @DecimalMin(value = "0.01")
        private BigDecimal tipAmount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PayBillShareRequest {
        @NotNull
        @Min(2)
        @Max(20)
        private Integer personCount;

        @NotNull
        @Min(1)
        private Integer shareNumber;

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
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateAdjustmentRequest {
        private Long orderId;

        @NotNull
        private BillAdjustmentType adjustmentType;

        @NotNull
        @DecimalMin(value = "0")
        private BigDecimal amount;

        @Size(max = 64)
        private String reason;

        @Size(max = 1000)
        private String reasonNote;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdjustmentResponse {
        private Long id;
        private Long billId;
        private Long orderId;
        private Long staffId;
        private BillAdjustmentType adjustmentType;
        private BigDecimal amount;
        private String reason;
        private String reasonNote;
        private LocalDateTime createdAt;
    }
}
