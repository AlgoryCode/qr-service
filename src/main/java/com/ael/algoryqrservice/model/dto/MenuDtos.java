package com.ael.algoryqrservice.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
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
        private Integer sortOrder;
        private String imageUrl;
        private Boolean available;
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
        private int sortOrder;
        private String imageUrl;
        private boolean available;
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
        private String themeId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MenuUpdateRequest {
        private String themeId;
        private String businessName;
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
