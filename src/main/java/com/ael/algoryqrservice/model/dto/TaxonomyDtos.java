package com.ael.algoryqrservice.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

public final class TaxonomyDtos {

    private TaxonomyDtos() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Document {
        private int version;
        private List<MainSeed> mains;
        private List<DescriptorSeed> descriptors;
        private List<TagSeed> tags;
        private List<AllergenSeed> allergens;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MainSeed {
        private Long id;
        private String slug;
        private String name;
        private Integer sortOrder;
        @Builder.Default
        private List<SubSeed> subs = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubSeed {
        private Long id;
        private String slug;
        private String name;
        private Integer sortOrder;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DescriptorSeed {
        private Long id;
        private Long subCategoryId;
        private String slug;
        private String name;
        private Integer sortOrder;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TagSeed {
        private Long id;
        private String slug;
        private String name;
        private Integer sortOrder;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AllergenSeed {
        private Long id;
        private String slug;
        private String name;
        private Integer sortOrder;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MainCategoryResponse {
        private Long id;
        private String slug;
        private String name;
        private int sortOrder;
        @Builder.Default
        private List<SubCategoryResponse> subs = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaxonomyPageResponse {
        private List<MainCategoryResponse> content;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
        private boolean hasNext;
        private String q;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubCategoryResponse {
        private Long id;
        private Long mainCategoryId;
        private String slug;
        private String name;
        private int sortOrder;
        @Builder.Default
        private List<DescriptorCategoryResponse> descriptors = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DescriptorCategoryResponse {
        private Long id;
        private Long subCategoryId;
        private String slug;
        private String name;
        private int sortOrder;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TagResponse {
        private Long id;
        private String slug;
        private String name;
        private int sortOrder;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AllergenResponse {
        private Long id;
        private String slug;
        private String name;
        private int sortOrder;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MainCategoryRequest {
        private Long id;
        private String slug;
        private String name;
        private Integer sortOrder;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubCategoryRequest {
        private Long id;
        private Long mainCategoryId;
        private String slug;
        private String name;
        private Integer sortOrder;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TagRequest {
        private Long id;
        private String slug;
        private String name;
        private Integer sortOrder;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AllergenRequest {
        private Long id;
        private String slug;
        private String name;
        private Integer sortOrder;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MainCategoryUpdateRequest {
        private String name;
        private Integer sortOrder;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubCategoryUpdateRequest {
        private Long mainCategoryId;
        private String name;
        private Integer sortOrder;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DescriptorCategoryRequest {
        private Long id;
        private Long subCategoryId;
        private String slug;
        private String name;
        private Integer sortOrder;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DescriptorCategoryUpdateRequest {
        private Long subCategoryId;
        private String name;
        private Integer sortOrder;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TagUpdateRequest {
        private String name;
        private Integer sortOrder;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AllergenUpdateRequest {
        private String name;
        private Integer sortOrder;
    }
}
