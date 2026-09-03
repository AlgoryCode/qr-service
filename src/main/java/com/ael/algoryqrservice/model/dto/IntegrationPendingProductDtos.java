package com.ael.algoryqrservice.model.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class IntegrationPendingProductDtos {

    private IntegrationPendingProductDtos() {
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private UUID id;
        private UUID jobId;
        private Long menuId;
        private String source;
        private String sourceProductId;
        private JsonNode productData;
        private BigDecimal confidence;
        private String approvalStatus;
        private Set<String> publishTargets;
        private List<String> warnings;
        private String errorMessage;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApprovalRequest {
        @NotEmpty
        private Set<String> publishTargets;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RejectRequest {
        @NotBlank
        private String reason;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BulkApproveRequest {
        @NotEmpty
        private List<UUID> productIds;
        @NotEmpty
        private Set<String> publishTargets;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRequest {
        private String name;
        private String description;
        private BigDecimal price;
        private String currency;
        private String category;
        private String subcategory;
        private Long subCategoryId;
        private String imageUrl;
        private JsonNode modifiers;
        private Boolean available;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JobAccepted {
        @NotNull
        private UUID jobId;
        private String status;
        private String direction;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JobUpdateRequest {
        private String status;
        private String aiBatchId;
        private String aiInputFileId;
        private String aiOutputFileId;
        private String errorMessage;
    }
}
