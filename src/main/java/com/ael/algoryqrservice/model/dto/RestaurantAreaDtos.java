package com.ael.algoryqrservice.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public final class RestaurantAreaDtos {

    private RestaurantAreaDtos() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateAreaRequest {
        @NotBlank
        @Size(max = 120)
        private String name;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateAreaRequest {
        @Size(max = 120)
        private String name;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AreaResponse {
        private Long id;
        private Long menuId;
        private String name;
        private int sortOrder;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
