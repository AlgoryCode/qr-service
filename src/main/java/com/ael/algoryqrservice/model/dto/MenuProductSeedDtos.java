package com.ael.algoryqrservice.model.dto;

import com.ael.algoryqrservice.model.nutrition.NutritionFacts;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class MenuProductSeedDtos {

    private MenuProductSeedDtos() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Document {
        private int version;
        private Long menuId;
        @Builder.Default
        private List<ProductSeed> products = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductSeed {
        private String name;
        private String description;
        private BigDecimal price;
        private String currency;
        private String subCategorySlug;
        @Builder.Default
        private List<String> tagSlugs = new ArrayList<>();
        @Builder.Default
        private List<String> allergenSlugs = new ArrayList<>();
        private Integer sortOrder;
        private Boolean available;
        private Integer servesPeopleMin;
        private Integer servesPeopleMax;
        private NutritionFacts nutrition;
    }
}
