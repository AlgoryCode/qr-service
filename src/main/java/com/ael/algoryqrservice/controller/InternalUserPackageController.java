package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.config.AuthServiceClientProperties;
import com.ael.algoryqrservice.service.UserPackageAssignmentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalUserPackageController {

    private final UserPackageAssignmentService userPackageAssignmentService;
    private final AuthServiceClientProperties authServiceClientProperties;

    @PostMapping("/{userId}/package")
    public ResponseEntity<Void> create(
            @PathVariable Long userId,
            @Valid @RequestBody CreateUserPackageRequest request,
            @RequestHeader(value = "X-Service-Token", required = false) String serviceTokenFallback,
            HttpServletRequest httpRequest
    ) {
        requireServiceToken(resolveToken(httpRequest, serviceTokenFallback));
        userPackageAssignmentService.create(userId, request.packageId(), request.packageCode());
        return ResponseEntity.noContent().build();
    }

    private String resolveToken(HttpServletRequest request, String fallback) {
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
        if (expected == null || expected.isBlank()) {
            return;
        }
        if (provided == null || !constantTimeEquals(expected, provided)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Service authentication failed");
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        byte[] left = expected.getBytes(StandardCharsets.UTF_8);
        byte[] right = actual.getBytes(StandardCharsets.UTF_8);
        if (left.length != right.length) {
            return false;
        }
        return MessageDigest.isEqual(left, right);
    }

    public record CreateUserPackageRequest(
            Long packageId,
            String packageCode
    ) {
    }
}
