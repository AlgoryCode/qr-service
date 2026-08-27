package com.ael.algoryqrservice.model.dto;

import com.ael.algoryqrservice.model.enums.MenuReservationStatus;
import com.ael.algoryqrservice.model.nutrition.NutritionFacts;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.AccessLevel;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
        private Long subCategoryId;
        @Setter(AccessLevel.NONE)
        private Long descriptorCategoryId;
        @JsonIgnore
        private boolean descriptorCategoryIdSpecified;
        private List<Long> tagIds;
        private List<Long> allergenIds;
        private Integer sortOrder;
        private String imageUrl;
        private Boolean available;
        private Boolean chefRecommended;
        private Integer servesPeopleMin;
        private Integer servesPeopleMax;
        private NutritionFacts nutrition;
        private MenuProductPairingsRequest pairings;

        @JsonProperty("descriptorCategoryId")
        public void setDescriptorCategoryId(Long descriptorCategoryId) {
            this.descriptorCategoryId = descriptorCategoryId;
            this.descriptorCategoryIdSpecified = true;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MenuProductPairingsRequest {
        private List<Long> productIds;
        private List<Long> mainCategoryIds;
        private List<Long> subCategoryIds;
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
        private Long subCategoryId;
        private String subCategorySlug;
        private String subCategoryName;
        private Long descriptorCategoryId;
        private String descriptorCategorySlug;
        private String descriptorCategoryName;
        private Long mainCategoryId;
        private String mainCategorySlug;
        private String mainCategoryName;
        private List<TaxonomyDtos.TagResponse> tags;
        private List<TaxonomyDtos.AllergenResponse> allergens;
        private int sortOrder;
        private String imageUrl;
        private boolean available;
        private boolean chefRecommended;
        private BigDecimal ratingAvg;
        private long ratingCount;
        private Integer servesPeopleMin;
        private Integer servesPeopleMax;
        private NutritionFacts nutrition;
        private MenuProductPairingsResponse pairings;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MenuProductPairingsResponse {
        private List<Long> productIds;
        private List<Long> mainCategoryIds;
        private List<Long> subCategoryIds;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductRatingRequest {
        @NotNull
        @Min(1)
        @Max(5)
        private Integer score;

        @Size(max = 500)
        private String comment;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductRatingResponse {
        private Long productId;
        private Long menuId;
        private int score;
        private String comment;
        private BigDecimal ratingAvg;
        private long ratingCount;
        private Integer userRating;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MenuRatingRequest {
        @NotNull
        @Min(1)
        @Max(5)
        private Integer score;

        @Size(max = 500)
        private String comment;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MenuRatingResponse {
        private Long menuId;
        private BigDecimal ratingAvg;
        private long ratingCount;
        private Integer userRating;
        private Integer score;
        private String comment;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeedbackItemResponse {
        private Long id;
        private String type;
        private Long productId;
        private String productName;
        private int score;
        private String comment;
        private String deviceType;
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeedbackPageResponse {
        private List<FeedbackItemResponse> content;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
        private boolean hasNext;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScoreHistogramBucket {
        private int score;
        private long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeedbackBucketSummary {
        private BigDecimal ratingAvg;
        private long ratingCount;
        private List<ScoreHistogramBucket> scoreHistogram;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeedbackSummaryResponse {
        private Long menuId;
        private FeedbackBucketSummary menu;
        private FeedbackBucketSummary products;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReservationCreateRequest {
        @NotBlank
        @Size(max = 120)
        private String customerName;

        @Size(max = 40)
        private String phone;

        @Email
        @Size(max = 255)
        private String email;

        @NotNull
        @Min(1)
        @Max(50)
        private Integer partySize;

        @NotNull
        private LocalDateTime reservationAt;

        @Size(max = 500)
        private String note;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReservationUpdateRequest {
        private MenuReservationStatus status;
        private LocalDateTime reservationAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReservationResponse {
        private Long id;
        private Long menuId;
        private String customerName;
        private String phone;
        private String email;
        private int partySize;
        private LocalDateTime reservationAt;
        private MenuReservationStatus status;
        private String note;
        private String deviceType;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReservationPageResponse {
        private List<ReservationResponse> content;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
        private boolean hasNext;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MenuProductPageResponse {
        private List<MenuProductResponse> content;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
        private boolean hasNext;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MenuProductsByQrResponse {
        private Long menuId;
        private Long qrId;
        private String businessName;
        private List<MenuProductResponse> content;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
        private boolean hasNext;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MenuCategoriesByQrResponse {
        private Long menuId;
        private Long qrId;
        private String businessName;
        private List<TaxonomyDtos.MainCategoryResponse> categories;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MenuProfileResponse {
        private Long menuId;
        private Long qrId;
        private Long branchId;
        private Long userId;
        private String themeId;
        private String businessName;
        private String slogan;
        private String chefName;
        private String chefDisplayName;
        private String chefAvatarKey;
        private String chefAvatarUrl;
        private String logoUrl;
        private String phone;
        private String email;
        private String address;
        private String publicUrl;
        private boolean active;
        private BigDecimal ratingAvg;
        private long ratingCount;
        private QrBrief qr;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChefAvatarItem {
        private String key;
        private String label;
        private String imageUrl;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QrBrief {
        private Long id;
        private String name;
        private String imgSrc;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActiveMenuSummary {
        private Long menuId;
        private Long qrId;
        private Long branchId;
        private String businessName;
        private String themeId;
        private String publicUrl;
        private boolean active;
        private QrNameBrief qr;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QrNameBrief {
        private Long id;
        private String name;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PublicMenuResponse {
        private MenuProfileResponse menu;
        private List<MenuProductResponse> products;
        private List<TaxonomyDtos.MainCategoryResponse> categories;
        private String themeId;
        private int productPage;
        private int productSize;
        private long productTotalElements;
        private boolean productHasNext;
        private int categoryPage;
        private int categorySize;
        private long categoryTotalElements;
        private boolean categoryHasNext;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MenuUpdateRequest {
        private String themeId;
        private String businessName;
        private String slogan;
        @Size(max = 80)
        private String chefName;
        @Size(max = 64)
        private String chefAvatarKey;
        @Size(max = 1024)
        private String logoUrl;
        private String phone;
        private String email;
        private String address;
        private Boolean active;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductSearchFilter {
        private Long mainCategoryId;
        private Long subCategoryId;
        private List<Long> tagIds;
        private List<Long> allergenIds;
        private Integer servesPeople;
        private Integer servesPeopleMin;
        private Integer servesPeopleMax;
        private String q;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TagFacetCount {
        private Long tagId;
        private String slug;
        private String name;
        private long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AllergenFacetCount {
        private Long allergenId;
        private String slug;
        private String name;
        private long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServesBucketFacet {
        private String key;
        private String label;
        private long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductFacetsResponse {
        private long totalMatching;
        private List<TagFacetCount> tags;
        private List<AllergenFacetCount> allergens;
        private List<ServesBucketFacet> servesBuckets;
    }
}
