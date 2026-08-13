package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.model.dto.LogoutRequest;
import com.ael.algoryqrservice.model.dto.MenuWaiterDtos;
import com.ael.algoryqrservice.model.dto.RefreshTokenRequest;
import com.ael.algoryqrservice.service.MenuWaiterAuthService;
import com.ael.algoryqrservice.util.ClientInfo;
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
@RequestMapping("/waiter/auth")
@RequiredArgsConstructor
public class WaiterAuthController {

    private final MenuWaiterAuthService menuWaiterAuthService;

    @PostMapping("/login")
    public ResponseEntity<MenuWaiterDtos.WaiterAuthResponse> login(
            @Valid @RequestBody MenuWaiterDtos.WaiterLoginRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(menuWaiterAuthService.login(request, ClientInfo.from(httpRequest)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<MenuWaiterDtos.WaiterAuthResponse> refresh(
            @Valid @RequestBody RefreshTokenRequest request
    ) {
        return ResponseEntity.ok(menuWaiterAuthService.refresh(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @RequestBody(required = false) LogoutRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        menuWaiterAuthService.logout(request, extractBearerToken(authorization));
        return ResponseEntity.ok(Map.of("message", "Çıkış başarılı"));
    }

    @GetMapping("/me")
    public ResponseEntity<MenuWaiterDtos.WaiterMeResponse> me() {
        return ResponseEntity.ok(menuWaiterAuthService.me());
    }

    private String extractBearerToken(String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return null;
    }
}
