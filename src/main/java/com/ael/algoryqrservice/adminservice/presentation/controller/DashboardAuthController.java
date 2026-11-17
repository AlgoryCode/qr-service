package com.ael.algoryqrservice.adminservice.presentation.controller;

import com.ael.algoryqrservice.adminservice.application.DashboardAccountService;
import com.ael.algoryqrservice.adminservice.application.DashboardAuthService;
import com.ael.algoryqrservice.adminservice.application.DashboardSecurityUtils;
import com.ael.algoryqrservice.adminservice.application.DashboardSessionService;
import com.ael.algoryqrservice.adminservice.dto.DashboardAuthDtos;
import com.ael.algoryqrservice.adminservice.infrastructure.persistence.entity.DashboardUser;
import com.ael.algoryqrservice.adminservice.infrastructure.persistence.entity.DashboardUserSession;
import com.ael.algoryqrservice.qrservice.account.service.JwtService;
import com.ael.algoryqrservice.qrservice.model.dto.AuthResponse;
import com.ael.algoryqrservice.qrservice.model.dto.LoginRequest;
import com.ael.algoryqrservice.qrservice.model.dto.LogoutRequest;
import com.ael.algoryqrservice.qrservice.model.dto.RefreshTokenRequest;
import com.ael.algoryqrservice.qrservice.util.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dashboard/auth")
@RequiredArgsConstructor
public class DashboardAuthController {

    private final DashboardAuthService dashboardAuthService;
    private final DashboardAccountService accountService;
    private final DashboardSessionService sessionService;
    private final DashboardSecurityUtils dashboardSecurityUtils;
    private final JwtService jwtService;

    /** Tokens, or {@code {mfaRequired, mfaToken}} when the account has a second factor. */
    @PostMapping("/login")
    public ResponseEntity<Object> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(dashboardAuthService.login(request, ClientInfo.from(httpRequest)));
    }

    @PostMapping("/login/mfa")
    public ResponseEntity<AuthResponse> loginMfa(
            @Valid @RequestBody DashboardAuthDtos.MfaLoginRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(dashboardAuthService.completeMfaLogin(
                request.mfaToken(), request.code(), ClientInfo.from(httpRequest)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(dashboardAuthService.refresh(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @RequestBody(required = false) LogoutRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        dashboardAuthService.logout(request, extractBearerToken(authorization));
        return ResponseEntity.ok(Map.of("message", "Çıkış başarılı"));
    }

    @GetMapping("/me")
    public ResponseEntity<DashboardAuthDtos.MeResponse> me() {
        DashboardUser user = dashboardSecurityUtils.getCurrentDashboardUser();
        return ResponseEntity.ok(new DashboardAuthDtos.MeResponse(
                user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(), user.getRole(),
                user.isActive(), user.getUsername(), user.isTotpEnabled(), user.getLastLoginAt()));
    }

    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody DashboardAuthDtos.ChangePasswordRequest request) {
        DashboardUser user = dashboardSecurityUtils.getCurrentDashboardUser();
        accountService.changeOwnPassword(user.getId(), request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/mfa/setup")
    public ResponseEntity<DashboardAuthDtos.MfaSetupResponse> mfaSetup() {
        return ResponseEntity.ok(accountService.startMfaSetup(dashboardSecurityUtils.getCurrentDashboardUser().getId()));
    }

    @PostMapping("/mfa/enable")
    public ResponseEntity<Void> mfaEnable(@Valid @RequestBody DashboardAuthDtos.CodeRequest request) {
        accountService.enableMfa(dashboardSecurityUtils.getCurrentDashboardUser().getId(), request.code());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/mfa/disable")
    public ResponseEntity<Void> mfaDisable(@Valid @RequestBody DashboardAuthDtos.CodeRequest request) {
        accountService.disableMfa(dashboardSecurityUtils.getCurrentDashboardUser().getId(), request.code());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<DashboardAuthDtos.SessionResponse>> sessions(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        UUID current = currentSessionId(authorization);
        Long userId = dashboardSecurityUtils.getCurrentDashboardUser().getId();
        return ResponseEntity.ok(sessionService.sessionsOf(userId).stream()
                .map(s -> toResponse(s, current))
                .toList());
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> revokeSession(@PathVariable UUID sessionId) {
        sessionService.revokeOwnedSession(dashboardSecurityUtils.getCurrentDashboardUser().getId(), sessionId);
        return ResponseEntity.noContent().build();
    }

    static DashboardAuthDtos.SessionResponse toResponse(DashboardUserSession s, UUID current) {
        return new DashboardAuthDtos.SessionResponse(s.getId(), s.getLoggedInAt(), s.getLastActivityAt(),
                s.getIpAddress(), s.getUserAgent(), s.getDevice(), s.isActive(), s.getId().equals(current));
    }

    private UUID currentSessionId(String authorization) {
        String token = extractBearerToken(authorization);
        return token == null ? null : jwtService.extractSessionIdIfSignatureValid(token).orElse(null);
    }

    private String extractBearerToken(String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return null;
    }
}
