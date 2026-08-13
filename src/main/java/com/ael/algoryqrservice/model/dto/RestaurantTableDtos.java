package com.ael.algoryqrservice.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public final class RestaurantTableDtos {

    private RestaurantTableDtos() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateTableRequest {
        @NotBlank
        @Size(max = 120)
        private String name;

        private Integer tableNumber;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateTableRequest {
        @Size(max = 120)
        private String name;

        private Integer tableNumber;

        private Boolean active;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TableResponse {
        private Long id;
        private Long menuId;
        private String name;
        private Integer tableNumber;
        private String publicToken;
        private String publicUrl;
        private String qrImageBase64;
        private boolean active;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OpenTableSessionRequest {
        /** Masa QR token; boşsa misafir (walk-in) oturumu açılır. */
        @Size(max = 64)
        private String tableToken;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TableSessionResponse {
        private String sessionToken;
        private Long tableId;
        private Long menuId;
        private String tableName;
        private LocalDateTime expiresAt;
    }
}
