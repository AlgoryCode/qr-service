package com.ael.algoryqrservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

public final class AiMenuImportClientDtos {

    private AiMenuImportClientDtos() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        private Long menuId;
        private Long userId;
        private List<String> imageUrls;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JobAccepted {
        private UUID jobId;
        private String status;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JobResponse {
        private UUID jobId;
        private Long menuId;
        private Long userId;
        private String status;
        private List<String> imageUrls;
        private Integer publishedCount;
        private Integer productCount;
        private String errorMessage;
        private String createdAt;
        private String completedAt;
    }
}
