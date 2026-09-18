package com.ael.algoryqrservice.store.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.function.Predicate;

@Component
@RequiredArgsConstructor
public class StoreTokenGenerator {

    private static final int TOKEN_BYTES = 12;
    private static final int MAX_ATTEMPTS = 8;

    private final SecureRandom secureRandom;

    public String generateUnique(Predicate<String> taken) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String candidate = generate();
            if (!taken.test(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Mağaza anahtarı üretilemedi");
    }

    private String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
