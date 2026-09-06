package com.ael.algoryqrservice.model.dto;

import com.ael.algoryqrservice.model.nutrition.NutritionFacts;
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
        private Long userId;
        private String status;
        private List<String> imageUrls;
        private Integer publishedCount;
        private Integer productCount;
        private String errorMessage;
        private LocalDateTime createdAt;
        private LocalDateTime completedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PublishRequest {
        @NotNull
        private Long menuId;
        @NotNull
        private Long userId;
        @NotEmpty
        private List<@NotNull PublishProduct> products;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PublishProduct {
        private String sourceProductId;
        private String name;
        private BigDecimal price;
        private String currency;
        private String category;
        private String subcategory;
        private Long subCategoryId;
        private String description;
        private String imageUrl;
        private Boolean available;
        private NutritionFacts nutrition;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PublishResponse {
        private Long menuId;
        private int createdCount;
        private List<Long> productIds;
    }
}
