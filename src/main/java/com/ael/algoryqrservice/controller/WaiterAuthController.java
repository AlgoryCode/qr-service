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

@RestController
@RequestMapping("/waiter/auth")
@RequiredArgsConstructor
public class WaiterAuthController {

    private final MenuWaiterAuthService menuWaiterAuthService;

    @PostMapping("/login")
    public MenuWaiterDtos.WaiterAuthResponse login(
            @Valid @RequestBody MenuWaiterDtos.WaiterLoginRequest request,
            HttpServletRequest httpRequest
    ) {
        return menuWaiterAuthService.login(request, ClientInfo.from(httpRequest));
    }

    @PostMapping("/refresh")
    public MenuWaiterDtos.WaiterAuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return menuWaiterAuthService.refresh(request);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestBody(required = false) LogoutRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        String accessToken = null;
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            accessToken = authorization.substring(7).trim();
        }
        menuWaiterAuthService.logout(request, accessToken);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public MenuWaiterDtos.WaiterMeResponse me() {
        return menuWaiterAuthService.me();
    }
}
