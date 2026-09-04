package com.ael.algoryqrservice.model.dto;

import com.ael.algoryqrservice.model.nutrition.NutritionFacts;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public final class AiMenuImportDtos {

    private AiMenuImportDtos() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateJobRequest {
        @NotEmpty
        private List<@NotNull @Size(min = 1, max = 2048) String> imageUrls;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JobAccepted {
        private UUID jobId;
        private String status;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JobResponse {
        private UUID jobId;
        private Long menuId;
        private String status;
        private List<String> imageUrls;
        private String errorMessage;
        private LocalDateTime createdAt;
        private LocalDateTime startedAt;
        private LocalDateTime finishedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DraftResponse {
        private UUID id;
        private UUID jobId;
        private Long menuId;
        private String sourceProductId;
        private JsonNode productData;
        private BigDecimal confidence;
        private String approvalStatus;
        private Long publishedProductId;
        private String rejectReason;
        private String errorMessage;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DraftUpdateRequest {
        private String name;
        private String description;
        private BigDecimal price;
        private String currency;
        private String category;
        private String subcategory;
        private Long subCategoryId;
        private String imageUrl;
        private Boolean available;
        private NutritionFacts nutrition;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RejectRequest {
        @Size(max = 1000)
        private String reason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BulkApproveRequest {
        @NotEmpty
        private List<@NotNull UUID> draftIds;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JobUpdateRequest {
        private String status;
        private String aiBatchId;
        private String aiInputFileId;
        private String aiOutputFileId;
        private String errorMessage;
        private JsonNode extractedProducts;
    }
}
