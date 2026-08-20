package com.ael.algoryqrservice.model.dto;

import com.ael.algoryqrservice.model.enums.MenuOrderStatus;
import com.ael.algoryqrservice.model.enums.TableBillStatus;
import com.ael.algoryqrservice.model.enums.WaiterCommissionScope;
import com.ael.algoryqrservice.model.enums.WaiterCommissionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class MenuWaiterDtos {

    private MenuWaiterDtos() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateWaiterRequest {
        @NotBlank
        @Size(min = 3, max = 64)
        private String username;

        @NotBlank
        @Size(min = 6, max = 255)
        private String password;

        @NotBlank
        @Size(max = 120)
        private String displayName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateWaiterRequest {
        @Size(max = 120)
        private String displayName;

        private Boolean active;

        @Size(min = 6, max = 255)
        private String password;

        private Boolean commissionEnabled;

        private WaiterCommissionType commissionType;

        private WaiterCommissionScope commissionScope;

        private BigDecimal commissionValue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WaiterResponse {
        private Long id;
        private Long menuId;
        private String username;
        private String displayName;
        private boolean active;
        private boolean commissionEnabled;
        private WaiterCommissionType commissionType;
        private WaiterCommissionScope commissionScope;
        private BigDecimal commissionValue;
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OwnerSummary {
        private Long id;
        private String firstName;
        private String lastName;
        private String email;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UsersPageResponse {
        private OwnerSummary owner;
        private List<WaiterResponse> waiters;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WaiterLoginRequest {
        @NotBlank
        @Size(max = 64)
        private String username;

        @NotBlank
        private String password;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WaiterAuthResponse {
        private String accessToken;
        private String refreshToken;
        private Long waiterId;
        private Long menuId;
        private String displayName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WaiterMeResponse {
        private Long waiterId;
        private Long menuId;
        private Long ownerUserId;
        private String username;
        private String displayName;
        private boolean active;
        private boolean commissionEnabled;
        private WaiterCommissionType commissionType;
        private BigDecimal commissionValue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WaiterNoteRequest {
        @Size(max = 2000)
        private String note;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CatalogProduct {
        private Long productId;
        private String name;
        private String description;
        private BigDecimal price;
        private String currency;
        private String imageUrl;
        private boolean available;
        private Long subCategoryId;
        private String subCategorySlug;
        private String subCategoryName;
        private Long mainCategoryId;
        private String mainCategoryName;
        private boolean commissionEligible;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CatalogResponse {
        private List<CatalogProduct> products;
        private boolean commissionEnabled;
        private WaiterCommissionType commissionType;
        private BigDecimal commissionValue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TableOrderSummary {
        private Long tableId;
        private String tableName;
        private Integer tableNumber;
        private boolean active;
        private int pendingOrderCount;
        private Long latestPendingOrderId;
        private MenuOrderStatus latestPendingStatus;
        private BigDecimal latestPendingTotal;
        private LocalDateTime latestPendingSubmittedAt;
        private TableBillStatus billStatus;
        private Long openBillId;
        private BigDecimal openBillTotal;
        private int openBillItemCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerListItem {
        private Long customerId;
        private Long menuId;
        private Long businessId;
        private String menuName;
        private Boolean menuDeleted;
        private String firstName;
        private String lastName;
        private String email;
        private LocalDateTime joinedAt;
        private LocalDateTime memberSince;
    }
}
