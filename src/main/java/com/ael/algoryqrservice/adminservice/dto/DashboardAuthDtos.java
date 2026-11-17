package com.ael.algoryqrservice.adminservice.dto;

import com.ael.algoryqrservice.adminservice.infrastructure.persistence.entity.enums.DashboardRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.UUID;

public final class DashboardAuthDtos {

    public static final String USERNAME_PATTERN = "^[a-zA-Z0-9._-]{3,64}$";
    public static final int MIN_PASSWORD = 10;

    private DashboardAuthDtos() {
    }

    public record MeResponse(
            Long id,
            String email,
            String firstName,
            String lastName,
            DashboardRole role,
            boolean active,
            String username,
            boolean mfaEnabled,
            LocalDateTime lastLoginAt
    ) {
    }

    /** Returned by login instead of tokens when the account uses a second factor. */
    public record MfaChallengeResponse(boolean mfaRequired, String mfaToken, long expiresInSeconds) {
    }

    public record MfaLoginRequest(@NotBlank String mfaToken, @NotBlank @Size(max = 12) String code) {
    }

    public record MfaSetupResponse(String secret, String otpauthUri, String qrPngBase64) {
    }

    public record CodeRequest(@NotBlank @Size(max = 12) String code) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = MIN_PASSWORD, max = 128, message = "Şifre en az 10 karakter olmalı") String newPassword
    ) {
    }

    public record SessionResponse(
            UUID id,
            LocalDateTime loggedInAt,
            LocalDateTime lastActivityAt,
            String ipAddress,
            String userAgent,
            String device,
            boolean active,
            boolean current
    ) {
    }

    public record DashboardUserResponse(
            Long id,
            String email,
            String username,
            String firstName,
            String lastName,
            DashboardRole role,
            boolean active,
            boolean mfaEnabled,
            LocalDateTime lastLoginAt,
            LocalDateTime createdAt
    ) {
    }

    public record CreateDashboardUserRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @Pattern(regexp = USERNAME_PATTERN, message = "Kullanıcı adı 3-64 karakter; harf, rakam, . _ -") String username,
            @NotBlank @Size(max = 255) String firstName,
            @NotBlank @Size(max = 255) String lastName,
            @NotNull DashboardRole role,
            @NotBlank @Size(min = MIN_PASSWORD, max = 128, message = "Şifre en az 10 karakter olmalı") String password
    ) {
    }

    public record UpdateDashboardUserRequest(
            @Size(max = 255) String firstName,
            @Size(max = 255) String lastName,
            @Pattern(regexp = USERNAME_PATTERN, message = "Kullanıcı adı 3-64 karakter; harf, rakam, . _ -") String username,
            DashboardRole role,
            Boolean active
    ) {
    }

    public record SetPasswordRequest(
            @NotBlank @Size(min = MIN_PASSWORD, max = 128, message = "Şifre en az 10 karakter olmalı") String password
    ) {
    }
}
