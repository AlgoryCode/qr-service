package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.config.AuthServiceClientProperties;
import com.ael.algoryqrservice.demo.provision.DemoProvisionPipeline;
import com.ael.algoryqrservice.model.dto.DemoProvisionRequest;
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
@RequestMapping("/internal/demo")
@RequiredArgsConstructor
public class InternalDemoController {

    private final DemoProvisionPipeline demoProvisionPipeline;
    private final AuthServiceClientProperties authServiceClientProperties;

    @PostMapping("/users/{userId}/provision")
    public ResponseEntity<Void> provision(
            @PathVariable Long userId,
            @Valid @RequestBody DemoProvisionRequest request,
            @RequestHeader(value = "X-Service-Token", required = false) String serviceTokenFallback,
            jakarta.servlet.http.HttpServletRequest httpRequest
    ) {
        requireServiceToken(resolveToken(httpRequest, serviceTokenFallback));
        demoProvisionPipeline.provision(userId, request.email(), request.displayName());
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
        if (expected == null || expected.isBlank()) {
            return;
        }
        if (provided == null || !constantTimeEquals(expected, provided)) {
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
