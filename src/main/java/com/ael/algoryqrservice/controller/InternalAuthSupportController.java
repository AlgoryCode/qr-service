package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.config.AuthServiceClientProperties;
import com.ael.algoryqrservice.model.dto.UserAccessProfile;
import com.ael.algoryqrservice.service.PackageActivationService;
import com.ael.algoryqrservice.service.UserAccessProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/internal/auth")
@RequiredArgsConstructor
public class InternalAuthSupportController {

    private final UserAccessProfileService userAccessProfileService;
    private final PackageActivationService packageActivationService;
    private final AuthServiceClientProperties authServiceClientProperties;

    @GetMapping("/users/{userId}/access-profile")
    public ResponseEntity<UserAccessProfile> accessProfile(
            @PathVariable Long userId,
            @RequestHeader(value = "X-Service-Token", required = false) String serviceTokenFallback,
            jakarta.servlet.http.HttpServletRequest request
    ) {
        requireServiceToken(resolveToken(request, serviceTokenFallback));
        return ResponseEntity.ok(userAccessProfileService.resolve(userId));
    }

    @PostMapping("/users/{userId}/ensure-subscription")
    public ResponseEntity<Void> ensureSubscription(
            @PathVariable Long userId,
            @RequestHeader(value = "X-Service-Token", required = false) String serviceTokenFallback,
            jakarta.servlet.http.HttpServletRequest request
    ) {
        requireServiceToken(resolveToken(request, serviceTokenFallback));
        packageActivationService.ensureSubscriptionState(userId);
        return ResponseEntity.noContent().build();
    }

    private String resolveToken(jakarta.servlet.http.HttpServletRequest request, String fallback) {
        String headerName = authServiceClientProperties.getHeaderName();
        if (headerName != null && !headerName.isBlank()) {
            String value = request.getHeader(headerName);
            if (value != null) {
                return value;
            }
        }
        return fallback;
    }

    private void requireServiceToken(String provided) {
        if (!authServiceClientProperties.isEnabled()) {
            return;
        }
        String expected = authServiceClientProperties.getToken();
        if (expected == null || expected.isBlank() || provided == null || !constantTimeEquals(expected, provided)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Service authentication failed");
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }
}
