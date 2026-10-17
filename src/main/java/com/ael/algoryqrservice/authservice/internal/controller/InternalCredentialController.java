package com.ael.algoryqrservice.authservice.internal.controller;

import com.ael.algoryqrservice.authservice.application.auth.EmailVerificationUseCase;
import com.ael.algoryqrservice.authservice.application.auth.PasswordChangeUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/credentials")
@RequiredArgsConstructor
public class InternalCredentialController {

    private final EmailVerificationUseCase emailVerificationUseCase;
    private final PasswordChangeUseCase passwordChangeUseCase;

    @PostMapping("/email-verification/status")
    public EmailVerificationUseCase.Status emailStatus(@Valid @RequestBody MerchantRequest request) {
        return emailVerificationUseCase.status(request.merchantId());
    }

    @PostMapping("/email-verification/request")
    public EmailVerificationUseCase.Status requestEmailCode(@Valid @RequestBody MerchantRequest request) {
        return emailVerificationUseCase.request(request.merchantId());
    }

    @PostMapping("/email-verification/resend")
    public ResponseEntity<Void> resendEmailCode(@Valid @RequestBody EmailRequest request) {
        emailVerificationUseCase.resend(request.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/email-verification/verify")
    public EmailVerificationUseCase.Status verifyEmail(@Valid @RequestBody VerifyRequest request) {
        if (request.merchantId() != null) {
            return emailVerificationUseCase.verify(request.merchantId(), request.code());
        }
        return emailVerificationUseCase.verifyByEmail(request.email(), request.code());
    }

    @PostMapping("/email-verification/send")
    public ResponseEntity<Void> sendEmailCode(@Valid @RequestBody MerchantRequest request) {
        emailVerificationUseCase.sendForAdmin(request.merchantId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/email-verification/reissue")
    public ResponseEntity<Void> reissueEmailCode(@Valid @RequestBody ReissueRequest request) {
        emailVerificationUseCase.reissue(request.merchantId(), request.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password-change/request")
    public PasswordChangeUseCase.CodeResponse requestPasswordCode(@Valid @RequestBody MerchantRequest request) {
        return passwordChangeUseCase.request(request.merchantId());
    }

    @PostMapping("/password-change/confirm")
    public ResponseEntity<Void> confirmPasswordChange(@Valid @RequestBody PasswordConfirmRequest request) {
        passwordChangeUseCase.confirm(
                request.merchantId(),
                request.code(),
                request.newPassword(),
                request.confirmPassword()
        );
        return ResponseEntity.noContent().build();
    }

    public record MerchantRequest(@NotNull Long merchantId) {
    }

    public record EmailRequest(
            @NotBlank @Email String email
    ) {
    }

    public record VerifyRequest(Long merchantId, String email, @NotBlank String code) {
    }

    public record ReissueRequest(@NotNull Long merchantId, @NotBlank @Email String email) {
    }

    public record PasswordConfirmRequest(
            @NotNull Long merchantId,
            @NotBlank String code,
            @NotBlank String newPassword,
            @NotBlank String confirmPassword
    ) {
    }
}
