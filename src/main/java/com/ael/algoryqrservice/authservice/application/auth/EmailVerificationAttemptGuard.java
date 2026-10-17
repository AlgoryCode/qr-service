package com.ael.algoryqrservice.authservice.application.auth;

import com.ael.algoryqrservice.authservice.infrastructure.persistence.entity.MerchantEntity;
import com.ael.algoryqrservice.authservice.infrastructure.persistence.repository.MerchantJpaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import com.ael.algoryqrservice.authservice.config.AuthTransactional;

import java.time.LocalDateTime;

@Component
public class EmailVerificationAttemptGuard {

    private final MerchantJpaRepository merchantRepository;
    private final int maxAttempts;
    private final int lockMinutes;

    public EmailVerificationAttemptGuard(
            MerchantJpaRepository merchantRepository,
            @Value("${app.email-verification.max-attempts:5}") int maxAttempts,
            @Value("${app.email-verification.lock-minutes:15}") int lockMinutes
    ) {
        this.merchantRepository = merchantRepository;
        this.maxAttempts = maxAttempts;
        this.lockMinutes = lockMinutes;
    }

    public boolean isLocked(MerchantEntity merchant, LocalDateTime now) {
        LocalDateTime lockedUntil = merchant.getEmailVerificationLockedUntil();
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    @AuthTransactional(propagation = Propagation.REQUIRES_NEW)
    public void registerFailure(Long merchantId) {
        merchantRepository.findById(merchantId).ifPresent(merchant -> {
            int attempts = merchant.getEmailVerificationAttempts() + 1;
            merchant.setEmailVerificationAttempts(attempts);
            if (attempts >= maxAttempts) {
                merchant.setEmailVerificationCodeHash(null);
                merchant.setEmailVerificationExpiresAt(null);
                merchant.setEmailVerificationLockedUntil(LocalDateTime.now().plusMinutes(lockMinutes));
            }
            merchantRepository.save(merchant);
        });
    }

    public void reset(MerchantEntity merchant) {
        merchant.setEmailVerificationAttempts(0);
        merchant.setEmailVerificationLockedUntil(null);
    }
}
