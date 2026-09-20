package com.ael.algoryqrservice.print.dto;

import com.ael.algoryqrservice.print.model.PrintJobStatus;
import com.ael.algoryqrservice.print.model.PrintJobType;
import com.ael.algoryqrservice.print.model.PrintSourceType;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

public final class PrintAgentDtos {

    private PrintAgentDtos() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreatePairingCodeRequest {
        @NotNull
        private Long branchId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PairingCodeResponse {
        private String code;
        private Long branchId;
        private LocalDateTime expiresAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PairDeviceRequest {
        @NotBlank
        @Size(min = 6, max = 6)
        private String code;

        @NotBlank
        @Size(max = 128)
        private String deviceName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PairDeviceResponse {
        private Long userId;
        private Long deviceId;
        private Long branchId;
        private String deviceToken;
        private String apiKey;
        private String deviceName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateApiKeyRequest {
        @NotNull
        private Long branchId;

        @Size(max = 128)
        private String deviceName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApiKeyResponse {
        private Long userId;
        private Long deviceId;
        private Long branchId;
        private String apiKey;
        private String deviceName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConnectRequest {
        @NotNull
        private Long userId;

        @NotNull
        private Long branchId;

        @NotBlank
        @Size(max = 200)
        private String apiKey;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConnectResponse {
        private Long userId;
        private Long deviceId;
        private Long branchId;
        private String deviceName;
        private boolean connected;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HeartbeatRequest {
        @Size(max = 255)
        private String printerName;

        @Size(max = 64)
        private String agentVersion;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceResponse {
        private Long id;
        private Long branchId;
        private String deviceName;
        private String printerName;
        private String agentVersion;
        private LocalDateTime lastSeenAt;
        private boolean enabled;
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JobResponse {
        private Long id;
        private PrintSourceType sourceType;
        private String sourceId;
        private PrintJobType jobType;
        private PrintJobStatus status;
        private JsonNode payload;
        private int attempts;
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PendingJobsResponse {
        private List<JobResponse> jobs;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AckRequest {
        @NotNull
        private PrintJobStatus status;

        @Size(max = 2000)
        private String error;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SetPrintKitchenRequest {
        @NotNull
        private Boolean enabled;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SettingsResponse {
        private Long userId;
        private Long branchId;
        private boolean printKitchenEnabled;
        private List<DeviceResponse> devices;
    }
}
