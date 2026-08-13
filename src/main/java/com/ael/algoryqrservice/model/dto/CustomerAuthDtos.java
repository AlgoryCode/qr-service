package com.ael.algoryqrservice.model.dto;

import com.ael.algoryqrservice.model.enums.AuthProvider;
import com.ael.algoryqrservice.model.enums.MembershipStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public final class CustomerAuthDtos {

    private CustomerAuthDtos() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerRegisterRequest {
        @NotBlank
        @Size(max = 255)
        private String firstName;

        @Size(max = 255)
        private String lastName;

        @NotBlank
        @Email
        @Size(max = 255)
        private String email;

        @NotBlank
        @Size(min = 6, max = 255)
        private String password;

        @NotBlank
        private String passwordConfirm;

        private Long menuId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerLoginRequest {
        @NotBlank
        @Email
        private String email;

        @NotBlank
        private String password;

        private Long menuId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerAuthResponse {
        private String accessToken;
        private String refreshToken;
        private Long customerId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerProfileResponse {
        private Long id;
        private String firstName;
        private String lastName;
        private String email;
        private String phone;
        private AuthProvider provider;
        private String avatarKey;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerProfilePatchRequest {
        @Size(max = 255)
        private String firstName;

        @Size(max = 255)
        private String lastName;

        @Size(max = 255)
        private String phone;

        @Size(max = 64)
        private String avatarKey;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerChangePasswordRequest {
        @NotBlank
        private String currentPassword;

        @NotBlank
        @Size(min = 6, max = 255)
        private String newPassword;

        @NotBlank
        private String newPasswordConfirm;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JoinMembershipRequest {
        @NotNull
        private Long menuId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MembershipResponse {
        private Long id;
        private Long customerId;
        private Long menuId;
        private MembershipStatus status;
        private LocalDateTime joinedAt;
    }
}
