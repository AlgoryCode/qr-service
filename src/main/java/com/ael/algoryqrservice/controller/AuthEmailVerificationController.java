package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.model.dto.EmailVerificationDtos;
import com.ael.algoryqrservice.service.AccountCredentialGateway;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/auth/email-verification")
@RequiredArgsConstructor
public class AuthEmailVerificationController {

    private final AccountCredentialGateway accountCredentialGateway;

    @PostMapping("/resend")
    public ResponseEntity<Map<String, String>> resend(
            @Valid @RequestBody EmailVerificationDtos.ResendByEmailRequest request
    ) {
        accountCredentialGateway.resendEmailCode(request);
        return ResponseEntity.ok(Map.of("message", "Doğrulama kodu gönderildi"));
    }

    @PostMapping("/verify")
    public ResponseEntity<EmailVerificationDtos.Status> verify(
            @Valid @RequestBody EmailVerificationDtos.PublicVerifyRequest request
    ) {
        return ResponseEntity.ok(accountCredentialGateway.verifyEmailByAddress(request));
    }
}
