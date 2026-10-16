package com.ael.algoryqrservice.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public final class RestaurantLayoutDtos {

    private RestaurantLayoutDtos() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateLayoutRequest {
        @NotBlank
        @Size(max = 120)
        private String name;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateLayoutRequest {
        @Size(max = 120)
        private String name;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssignBranchRequest {
        private Long branchId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LayoutResponse {
        private Long id;
        private String name;
        private Long branchId;
        private String branchName;
        private int areaCount;
        private int tableCount;
        private boolean menuLinked;
    }
}
