package com.ael.algoryqrservice.qrservice.account.service;

import com.ael.algoryqrservice.qrservice.client.AuthCredentialClient;
import com.ael.algoryqrservice.qrservice.model.User;
import com.ael.algoryqrservice.qrservice.account.dto.AccountDtos;
import com.ael.algoryqrservice.qrservice.account.dto.EmailVerificationDtos;
import com.ael.algoryqrservice.qrservice.repository.UserRepository;
import com.ael.algoryqrservice.qrservice.security.EmailVerificationGate;
import com.ael.algoryqrservice.qrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountCredentialGateway {

    private final AuthCredentialClient authCredentialClient;
    private final SecurityUtils securityUtils;
    private final UserRepository userRepository;
    private final EmailVerificationGate emailVerificationGate;

    public EmailVerificationDtos.Status emailStatus() {
        Long merchantId = securityUtils.getCurrentMerchantId();
        EmailVerificationDtos.Status status = authCredentialClient.emailStatus(merchantId);
        syncVerified(merchantId, status.verified());
        return status;
    }

    public EmailVerificationDtos.Status requestEmailCode() {
        Long merchantId = securityUtils.getCurrentMerchantId();
        EmailVerificationDtos.Status status = authCredentialClient.requestEmailCode(merchantId);
        syncVerified(merchantId, status.verified());
        return status;
    }

    public EmailVerificationDtos.Status verifyEmail(EmailVerificationDtos.VerifyRequest request) {
        Long merchantId = securityUtils.getCurrentMerchantId();
        EmailVerificationDtos.Status status = authCredentialClient.verifyEmail(merchantId, request.code());
        syncVerified(merchantId, status.verified());
        return status;
    }

    public void resendEmailCode(EmailVerificationDtos.ResendByEmailRequest request) {
        authCredentialClient.resendEmailCode(request.email());
    }

    public EmailVerificationDtos.Status verifyEmailByAddress(EmailVerificationDtos.PublicVerifyRequest request) {
        EmailVerificationDtos.Status status = authCredentialClient.verifyEmailByAddress(request.email(), request.code());
        if (status.verified()) {
            userRepository.findByEmail(request.email().trim().toLowerCase())
                    .ifPresent(user -> syncVerified(user.getId(), true));
        }
        return status;
    }

    public AccountDtos.PasswordChangeCodeResponse requestPasswordChange() {
        return authCredentialClient.requestPasswordChange(securityUtils.getCurrentMerchantId());
    }

    public void confirmPasswordChange(AccountDtos.ConfirmPasswordChangeRequest request) {
        authCredentialClient.confirmPasswordChange(
                securityUtils.getCurrentMerchantId(),
                request.getCode(),
                request.getNewPassword(),
                request.getConfirmPassword()
        );
    }

    private void syncVerified(Long merchantId, boolean verified) {
        if (!verified || merchantId == null) {
            return;
        }
        userRepository.findById(merchantId).ifPresent(this::markLocalVerified);
        emailVerificationGate.markVerified(merchantId);
    }

    private void markLocalVerified(User user) {
        if (user.isEmailVerified()
                && user.getEmailVerificationCodeHash() == null
                && user.getEmailVerificationLockedUntil() == null) {
            return;
        }
        user.setEmailVerified(true);
        user.setEmailVerificationCodeHash(null);
        user.setEmailVerificationExpiresAt(null);
        user.setEmailVerificationAttempts(0);
        user.setEmailVerificationLockedUntil(null);
        userRepository.save(user);
    }
}
