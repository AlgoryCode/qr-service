package com.ael.algoryqrservice.model.dto;

import com.ael.algoryqrservice.model.enums.AuthProvider;
import com.ael.algoryqrservice.model.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
        private boolean trialUsed;
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
