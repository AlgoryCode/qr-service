package com.ael.algoryqrservice.integration.ubereatsmenu.model.dto;

import com.ael.algoryqrservice.integration.ubereatsmenu.model.UberEatsMenuConnectionStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

public final class UberEatsMenuDtos {

    private UberEatsMenuDtos() {
    }

    public record Credentials(String clientId, String clientSecret, String storeId) {
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpsertConnectionRequest {
        @NotNull
        private Long menuId;
        @NotBlank
        private String storeId;
        private String clientId;
        private String clientSecret;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConnectionResponse {
        private Long id;
        private Long menuId;
        private String storeId;
        private String clientIdMasked;
        private UberEatsMenuConnectionStatus status;
        private String lastError;
        private LocalDateTime lastSyncedAt;
        private LocalDateTime updatedAt;
    }
}
