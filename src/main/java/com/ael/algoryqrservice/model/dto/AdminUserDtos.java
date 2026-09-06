package com.ael.algoryqrservice.model.dto;

import com.ael.algoryqrservice.model.enums.AuthProvider;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.model.enums.UserRole;
import com.ael.algoryqrservice.trial.domain.TrialLifecycle;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.LocalDateTime;
import java.util.List;

public final class AdminUserDtos {

    private AdminUserDtos() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserSummaryResponse {
        private Long id;
        private String firstName;
        private String lastName;
        private String displayName;
        private String email;
        private String phone;
        private AuthProvider provider;
        private UserRole role;
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserPageResponse {
        private List<UserSummaryResponse> content;
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
    public static class UserDetailResponse {
        private Long id;
        private String firstName;
        private String lastName;
        private String displayName;
        private String email;
        private String phone;
        private AuthProvider provider;
        private UserRole role;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private TrialLifecycle trialLifecycle;
        private boolean trialConsumed;
        private LocalDateTime trialExpiresAt;
        private String registrationIpAddress;
        private String registrationDevice;
        private String registrationDeviceType;
        private String activePackage;
        private List<String> products;
        private List<String> scopes;
        private long qrCount;
        private long activeMenuCount;
        private List<PurchaseResponse> purchases;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExtendTrialRequest {
        @Min(1)
        @Max(365)
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
    public static class ReactivatePackageRequest {
        @Min(1)
        @Max(3650)
        private int days;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PackageLifecycleResponse {
        private Long purchaseId;
        private String packageName;
        private PurchaseStatus status;
        private LocalDateTime expiresAt;
        private Integer daysAdded;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImpersonateResponse {
        private String accessToken;
        private String refreshToken;
        private Long userId;
        private String email;
        private String firstName;
        private String lastName;
        private Long impersonatorUserId;
    }
}
