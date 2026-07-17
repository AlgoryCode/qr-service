package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.model.DashboardUser;
import com.ael.algoryqrservice.model.dto.AuthResponse;
import com.ael.algoryqrservice.model.dto.DashboardAuthDtos;
import com.ael.algoryqrservice.model.dto.LoginRequest;
import com.ael.algoryqrservice.model.dto.LogoutRequest;
import com.ael.algoryqrservice.model.dto.RefreshTokenRequest;
import com.ael.algoryqrservice.service.DashboardAuthService;
import com.ael.algoryqrservice.util.ClientInfo;
import com.ael.algoryqrservice.util.DashboardSecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/dashboard/auth")
@RequiredArgsConstructor
public class DashboardAuthController {

    private final DashboardAuthService dashboardAuthService;
    private final DashboardSecurityUtils dashboardSecurityUtils;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(dashboardAuthService.login(request, ClientInfo.from(httpRequest)));
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
        return ResponseEntity.ok(Map.of("message", "Cikis basarili"));
    }

    @GetMapping("/me")
    public ResponseEntity<DashboardAuthDtos.MeResponse> me() {
        DashboardUser user = dashboardSecurityUtils.getCurrentDashboardUser();
        return ResponseEntity.ok(new DashboardAuthDtos.MeResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole(),
                user.isActive()
        ));
    }

    private String extractBearerToken(String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return null;
    }
}
