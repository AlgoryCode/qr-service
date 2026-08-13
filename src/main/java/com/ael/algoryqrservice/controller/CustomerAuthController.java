package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.model.dto.CustomerAuthDtos;
import com.ael.algoryqrservice.model.dto.LogoutRequest;
import com.ael.algoryqrservice.model.dto.RefreshTokenRequest;
import com.ael.algoryqrservice.service.CustomerAccountService;
import com.ael.algoryqrservice.service.CustomerAuthService;
import com.ael.algoryqrservice.util.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/customer/auth")
@RequiredArgsConstructor
public class CustomerAuthController {

    private final CustomerAuthService customerAuthService;
    private final CustomerAccountService customerAccountService;

    @PostMapping("/register")
    public ResponseEntity<CustomerAuthDtos.CustomerAuthResponse> register(
            @Valid @RequestBody CustomerAuthDtos.CustomerRegisterRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(customerAuthService.register(request, ClientInfo.from(httpRequest)));
    }

    @PostMapping("/login")
    public ResponseEntity<CustomerAuthDtos.CustomerAuthResponse> login(
            @Valid @RequestBody CustomerAuthDtos.CustomerLoginRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(customerAuthService.login(request, ClientInfo.from(httpRequest)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<CustomerAuthDtos.CustomerAuthResponse> refresh(
            @Valid @RequestBody RefreshTokenRequest request
    ) {
        return ResponseEntity.ok(customerAuthService.refresh(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @RequestBody(required = false) LogoutRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        customerAuthService.logout(request, extractBearerToken(authorization));
        return ResponseEntity.ok(Map.of("message", "Çıkış başarılı"));
    }

    @GetMapping("/me")
    public ResponseEntity<CustomerAuthDtos.CustomerProfileResponse> me() {
        return ResponseEntity.ok(customerAccountService.getMyProfile());
    }

    private String extractBearerToken(String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return null;
    }
}
