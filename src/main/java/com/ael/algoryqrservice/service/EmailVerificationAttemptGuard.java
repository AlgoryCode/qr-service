package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class EmailVerificationAttemptGuard {

    private final UserRepository userRepository;

    @Value("${app.email-verification.max-attempts:5}")
    private int maxAttempts;

    @Value("${app.email-verification.lock-minutes:15}")
    private int lockMinutes;

    public boolean isLocked(User user, LocalDateTime now) {
        LocalDateTime lockedUntil = user.getEmailVerificationLockedUntil();
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registerFailure(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            int attempts = user.getEmailVerificationAttempts() + 1;
            user.setEmailVerificationAttempts(attempts);
            if (attempts >= maxAttempts) {
                user.setEmailVerificationCodeHash(null);
                user.setEmailVerificationExpiresAt(null);
                user.setEmailVerificationLockedUntil(LocalDateTime.now().plusMinutes(lockMinutes));
            }
            userRepository.save(user);
        });
    }

    public void reset(User user) {
        user.setEmailVerificationAttempts(0);
        user.setEmailVerificationLockedUntil(null);
    }
}
