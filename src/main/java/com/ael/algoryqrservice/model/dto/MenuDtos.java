package com.ael.algoryqrservice.model.dto;

import com.ael.algoryqrservice.model.nutrition.NutritionFacts;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class MenuDtos {

    private MenuDtos() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MenuProductRequest {
        private String name;
        private String description;
        private BigDecimal price;
        private String currency;
        private String category;
        private Long categoryId;
        private Integer sortOrder;
        private String imageUrl;
        private Boolean available;
        private NutritionFacts nutrition;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MenuProductResponse {
        private Long productId;
        private Long menuId;
        private String name;
        private String description;
        private BigDecimal price;
        private String currency;
        private String category;
        private Long categoryId;
        private String categoryName;
        private String categoryPath;
        private int sortOrder;
        private String imageUrl;
        private boolean available;
        private NutritionFacts nutrition;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MenuCategoryRequest {
        private String name;
        private Long parentId;
        private Integer sortOrder;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MenuCategoryUpdateRequest {
        private String name;
        private Long parentId;
        private Integer sortOrder;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MenuCategoryResponse {
        private Long categoryId;
        private Long menuId;
        private Long parentId;
        private String name;
        private int sortOrder;
        @Builder.Default
        private List<MenuCategoryResponse> children = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MenuProfileResponse {
        private Long menuId;
        private Long qrId;
        private Long userId;
        private String themeId;
        private String businessName;
        private String slogan;
        private String phone;
        private String email;
        private String address;
        private String publicSlug;
        private String urlMode;
        private String publicUrl;
        private boolean active;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PublicMenuResponse {
        private MenuProfileResponse menu;
        private List<MenuProductResponse> products;
        private List<MenuCategoryResponse> categories;
        private String themeId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MenuUpdateRequest {
        private String themeId;
        private String businessName;
        private String slogan;
        private String phone;
        private String email;
        private String address;
        private String urlMode;
        private String publicSlug;
        private Boolean active;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SlugAvailabilityResponse {
        private String slug;
        private boolean available;
    }
}
