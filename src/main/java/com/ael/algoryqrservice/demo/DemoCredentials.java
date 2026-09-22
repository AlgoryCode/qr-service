package com.ael.algoryqrservice.demo;

import com.ael.algoryqrservice.model.dto.LoginRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
@RequiredArgsConstructor
public class DemoCredentials {

    private final DemoProperties properties;

    public boolean accept(LoginRequest request) {
        if (!properties.isConfigured() || request == null) {
            return false;
        }
        if (request.getEmail() == null || request.getPassword() == null) {
            return false;
        }
        return emailMatches(request.getEmail()) && secretMatches(request.getPassword());
    }

    public String email() {
        return properties.normalizedEmail();
    }

    private boolean emailMatches(String provided) {
        return email().equals(provided.trim().toLowerCase());
    }

    private boolean secretMatches(String provided) {
        byte[] expected = properties.getPassword().getBytes(StandardCharsets.UTF_8);
        byte[] actual = provided.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }
}
