package com.ael.algoryqrservice.adminservice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Shapes of the admin panel's user trial/package actions (admin-qr-dashboard src/lib/api/types.ts).
 * {@code purchaseId} is null unless the package came from a purchase: trials and admin grants
 * have none in the fulfillment model.
 */
public final class AdminPackageDtos {

    private AdminPackageDtos() {
    }

    /** PATCH /trial body: {@code {days}} extends, {@code {status:"ENDED"}} ends the trial. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrialUpdateRequest {
        @Min(1)
        @Max(365)
        private Integer days;
        private String status;
    }

    /** POST /trial body. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExtendTrialRequest {
        @Min(1)
        @Max(365)
        private int days;
    }

    /**
     * PATCH /package body: {@code {status:"INACTIVE"}} deactivates, {@code {status:"ACTIVE", days}}
     * (or just {@code {days}}) grants access for that many days.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PackageUpdateRequest {
        private String status;
        @Min(1)
        @Max(3650)
        private Integer days;
    }

    /** POST /package body. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReactivatePackageRequest {
        @Min(1)
        @Max(3650)
        private int days;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExtendTrialResponse {
        private Long purchaseId;
        private String packageName;
        private LocalDateTime expiresAt;
        private int daysAdded;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EndTrialResponse {
        private Long purchaseId;
        private String packageName;
        private LocalDateTime expiresAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PackageLifecycleResponse {
        private Long purchaseId;
        private String packageName;
        /** ACTIVE or EXPIRED, matching the panel's PurchaseStatus. */
        private String status;
        private LocalDateTime expiresAt;
        private Integer daysAdded;
    }
}
