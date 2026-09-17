package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.model.dto.*;
import com.ael.algoryqrservice.service.AuthService;
import com.ael.algoryqrservice.service.EmailVerificationService;
import com.ael.algoryqrservice.util.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest
    ) {
        RegisterResponse response = authService.register(request, ClientInfo.from(httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(authService.login(request, ClientInfo.from(httpRequest)));
    }

    @PostMapping("/email-verification/resend")
    public ResponseEntity<Map<String, String>> resendEmailVerification(
            @Valid @RequestBody EmailVerificationDtos.ResendByEmailRequest request
    ) {
        emailVerificationService.resendByEmail(request);
        return ResponseEntity.ok(Map.of("message", "Doğrulama kodu gönderildi"));
    }

    @PostMapping("/email-verification/verify")
    public ResponseEntity<EmailVerificationDtos.Status> verifyEmail(
            @Valid @RequestBody EmailVerificationDtos.PublicVerifyRequest request
    ) {
        return ResponseEntity.ok(emailVerificationService.verifyByEmail(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @RequestBody(required = false) LogoutRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        String accessToken = extractBearerToken(authorization);
        authService.logout(request, accessToken);
        return ResponseEntity.ok(Map.of("message", "Çıkış başarılı"));
    }

    @GetMapping("/access-profile")
    public ResponseEntity<UserAccessProfile> getAccessProfile() {
        return ResponseEntity.ok(authService.getAccessProfile());
    }

    @GetMapping("/sessions")
    public ResponseEntity<SessionPageResponse> getMySessions(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(authService.getMySessions(extractBearerToken(authorization), page, size));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Map<String, String>> revokeSession(@PathVariable UUID sessionId) {
        authService.revokeSession(sessionId);
        return ResponseEntity.ok(Map.of("message", "Oturum iptal edildi"));
    }

    private String extractBearerToken(String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return null;
    }
}
