package com.ael.algoryqrservice.authservice.security;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Checks a password in roughly constant time whether or not the account exists.
 *
 * <p>Hashing is the slow part of a login. If an unknown account skipped it, the response time alone
 * would tell an attacker which e-mails/usernames are registered. When there is no stored hash we
 * still hash against a throwaway value and report a mismatch.
 */
@Component
public class PasswordVerifier {

    private final PasswordEncoder passwordEncoder;
    private final String dummyHash;

    public PasswordVerifier(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /** True only when {@code storedHash} exists and matches; always pays the cost of one hash check. */
    public boolean matches(String rawPassword, String storedHash) {
        if (storedHash == null || storedHash.isBlank()) {
            passwordEncoder.matches(rawPassword, dummyHash);
            return false;
        }
        return passwordEncoder.matches(rawPassword, storedHash);
    }
}
