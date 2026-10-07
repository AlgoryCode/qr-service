package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.client.AuthCredentialClient;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.dto.AccountDtos;
import com.ael.algoryqrservice.model.dto.EmailVerificationDtos;
import com.ael.algoryqrservice.repository.UserRepository;
import com.ael.algoryqrservice.security.EmailVerificationGate;
import com.ael.algoryqrservice.util.SecurityUtils;
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
        Long userId = securityUtils.getCurrentUserId();
        EmailVerificationDtos.Status status = authCredentialClient.emailStatus(userId);
        syncVerified(userId, status.verified());
        return status;
    }

    public EmailVerificationDtos.Status requestEmailCode() {
        Long userId = securityUtils.getCurrentUserId();
        EmailVerificationDtos.Status status = authCredentialClient.requestEmailCode(userId);
        syncVerified(userId, status.verified());
        return status;
    }

    public EmailVerificationDtos.Status verifyEmail(EmailVerificationDtos.VerifyRequest request) {
        Long userId = securityUtils.getCurrentUserId();
        EmailVerificationDtos.Status status = authCredentialClient.verifyEmail(userId, request.code());
        syncVerified(userId, status.verified());
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
        return authCredentialClient.requestPasswordChange(securityUtils.getCurrentUserId());
    }

    public void confirmPasswordChange(AccountDtos.ConfirmPasswordChangeRequest request) {
        authCredentialClient.confirmPasswordChange(
                securityUtils.getCurrentUserId(),
                request.getCode(),
                request.getNewPassword(),
                request.getConfirmPassword()
        );
    }

    private void syncVerified(Long userId, boolean verified) {
        if (!verified || userId == null) {
            return;
        }
        userRepository.findById(userId).ifPresent(this::markLocalVerified);
        emailVerificationGate.markVerified(userId);
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
