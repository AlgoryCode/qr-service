package com.ael.algoryqrservice.qrservice.client;

import com.ael.algoryqrservice.authservice.application.auth.EmailVerificationUseCase;
import com.ael.algoryqrservice.authservice.application.auth.PasswordChangeUseCase;
import com.ael.algoryqrservice.qrservice.account.dto.AccountDtos;
import com.ael.algoryqrservice.qrservice.account.dto.EmailVerificationDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthCredentialClient {

    private final EmailVerificationUseCase emailVerificationUseCase;
    private final PasswordChangeUseCase passwordChangeUseCase;

    public EmailVerificationDtos.Status emailStatus(Long merchantId) {
        return toStatus(emailVerificationUseCase.status(merchantId));
    }

    public EmailVerificationDtos.Status requestEmailCode(Long merchantId) {
        return toStatus(emailVerificationUseCase.request(merchantId));
    }

    public void resendEmailCode(String email) {
        emailVerificationUseCase.resend(email);
    }

    public EmailVerificationDtos.Status verifyEmail(Long merchantId, String code) {
        return toStatus(emailVerificationUseCase.verify(merchantId, code));
    }

    public EmailVerificationDtos.Status verifyEmailByAddress(String email, String code) {
        return toStatus(emailVerificationUseCase.verifyByEmail(email, code));
    }

    public void sendEmailCode(Long merchantId) {
        emailVerificationUseCase.sendForAdmin(merchantId);
    }

    public void reissueEmailCode(Long merchantId, String email) {
        emailVerificationUseCase.reissue(merchantId, email);
    }

    public AccountDtos.PasswordChangeCodeResponse requestPasswordChange(Long merchantId) {
        PasswordChangeUseCase.CodeResponse response = passwordChangeUseCase.request(merchantId);
        return AccountDtos.PasswordChangeCodeResponse.builder()
                .maskedEmail(response.maskedEmail())
                .expiresInSeconds(response.expiresInSeconds())
                .validityMinutes(response.validityMinutes())
                .build();
    }

    public void confirmPasswordChange(Long merchantId, String code, String newPassword, String confirmPassword) {
        passwordChangeUseCase.confirm(merchantId, code, newPassword, confirmPassword);
    }

    private EmailVerificationDtos.Status toStatus(EmailVerificationUseCase.Status status) {
        return new EmailVerificationDtos.Status(status.verified(), status.email(), status.expiresAt());
    }
}
