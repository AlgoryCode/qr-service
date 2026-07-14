package com.ael.algoryqrservice.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

public final class AccountDtos {

    private AccountDtos() {
    }

    @Data
    @Builder
    public static class MyProfileResponse {
        private Long userId;
        private String firstName;
        private String lastName;
        private String email;
        private String phoneNumber;
        private boolean twoFactorEnabled;
        private String memberSince;
        private boolean notifyEmailImportant;
        private boolean notifyScanAlerts;
        private boolean notifyWeeklyReport;
        private boolean notifyMarketingEmails;
        private boolean notifyPushBrowser;
    }

    @Data
    public static class MyProfilePatchRequest {
        private String firstName;
        private String lastName;

        @Email(message = "Geçerli bir e-posta adresi giriniz")
        private String email;

        private String phoneNumber;
        private Boolean notifyEmailImportant;
        private Boolean notifyScanAlerts;
        private Boolean notifyWeeklyReport;
        private Boolean notifyMarketingEmails;
        private Boolean notifyPushBrowser;
    }

    @Data
    public static class ChangePasswordRequest {
        @NotBlank(message = "Mevcut şifre zorunludur")
        private String currentPassword;

        @NotBlank(message = "Yeni şifre zorunludur")
        @Size(min = 8, message = "Yeni şifre en az 8 karakter olmalıdır")
        private String newPassword;
    }

    @Data
    @Builder
    public static class PasswordChangeCodeResponse {
        private String maskedEmail;
        private int expiresInSeconds;
        private int validityMinutes;
    }

    @Data
    public static class ConfirmPasswordChangeRequest {
        @NotBlank(message = "Doğrulama kodu zorunludur")
        @Size(min = 6, max = 6, message = "Doğrulama kodu 6 haneli olmalıdır")
        @jakarta.validation.constraints.Pattern(regexp = "\\d{6}", message = "Doğrulama kodu 6 haneli sayı olmalıdır")
        private String code;

        @NotBlank(message = "Yeni şifre zorunludur")
        @Size(min = 8, message = "Yeni şifre en az 8 karakter olmalıdır")
        private String newPassword;

        @NotBlank(message = "Şifre tekrarı zorunludur")
        private String confirmPassword;
    }

    @Data
    @Builder
    public static class EmailChangeCodeResponse {
        private String challengeId;
        private String maskedEmail;
        private int expiresInSeconds;
        private int validityMinutes;
    }

    @Data
    public static class EmailChangeVerifyCurrentRequest {
        @NotBlank(message = "challengeId zorunludur")
        private String challengeId;

        @NotBlank(message = "Doğrulama kodu zorunludur")
        @jakarta.validation.constraints.Pattern(regexp = "\\d{6}", message = "Doğrulama kodu 6 haneli sayı olmalıdır")
        private String code;
    }

    @Data
    public static class EmailChangeRequestNewCodeRequest {
        @NotBlank(message = "challengeId zorunludur")
        private String challengeId;

        @NotBlank(message = "Yeni e-posta zorunludur")
        @Email(message = "Geçerli bir e-posta adresi giriniz")
        private String newEmail;
    }

    @Data
    public static class EmailChangeConfirmRequest {
        @NotBlank(message = "challengeId zorunludur")
        private String challengeId;

        @NotBlank(message = "Doğrulama kodu zorunludur")
        @jakarta.validation.constraints.Pattern(regexp = "\\d{6}", message = "Doğrulama kodu 6 haneli sayı olmalıdır")
        private String code;
    }
}
