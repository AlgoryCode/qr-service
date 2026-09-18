package com.ael.algoryqrservice.security;

import com.ael.algoryqrservice.model.enums.AuthProvider;
import com.ael.algoryqrservice.repository.UserRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class EmailVerificationGate {

    private static final Duration VERIFIED_TTL = Duration.ofMinutes(10);
    private static final int MAX_CACHED_USERS = 50_000;

    private final UserRepository userRepository;
    private final Cache<Long, Boolean> verifiedUsers;

    public EmailVerificationGate(UserRepository userRepository) {
        this.userRepository = userRepository;
        this.verifiedUsers = Caffeine.newBuilder()
                .expireAfterWrite(VERIFIED_TTL)
                .maximumSize(MAX_CACHED_USERS)
                .build();
    }

    public boolean isVerificationPending(Long userId) {
        if (userId == null) {
            return false;
        }
        if (Boolean.TRUE.equals(verifiedUsers.getIfPresent(userId))) {
            return false;
        }
        boolean verified = userRepository.findById(userId)
                .map(user -> user.getProvider() != AuthProvider.BASIC || user.isEmailVerified())
                .orElse(true);
        if (verified) {
            verifiedUsers.put(userId, true);
        }
        return !verified;
    }

    public void markVerified(Long userId) {
        if (userId == null) {
            return;
        }
        verifiedUsers.put(userId, true);
    }
}
