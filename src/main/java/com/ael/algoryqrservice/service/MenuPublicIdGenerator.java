package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.repository.MenuRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

@Component
@RequiredArgsConstructor
public class MenuPublicIdGenerator {

    private static final int TOKEN_BYTES = 16;
    private static final int MAX_ATTEMPTS = 8;

    private final SecureRandom secureRandom;
    private final MenuRepository menuRepository;

    public String generateUnique() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String candidate = generate();
            if (!menuRepository.existsByPublicId(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("public_id üretilemedi");
    }

    private String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
