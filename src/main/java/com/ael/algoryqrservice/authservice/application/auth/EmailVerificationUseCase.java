package com.ael.algoryqrservice.authservice.application.auth;

import com.ael.algoryqrservice.authservice.exception.BadRequestException;
import com.ael.algoryqrservice.authservice.exception.NotFoundException;
import com.ael.algoryqrservice.authservice.infrastructure.client.QrVerificationMailClient;
import com.ael.algoryqrservice.authservice.infrastructure.persistence.entity.MerchantEntity;
import com.ael.algoryqrservice.authservice.infrastructure.persistence.repository.MerchantJpaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.ael.algoryqrservice.authservice.config.AuthTransactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
public class EmailVerificationUseCase {

    private final MerchantJpaRepository merchantRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationAttemptGuard attemptGuard;
    private final QrVerificationMailClient mailClient;
    private final SecureRandom secureRandom;
    private final int codeValidityMinutes;

    public EmailVerificationUseCase(
            MerchantJpaRepository merchantRepository,
            PasswordEncoder passwordEncoder,
            EmailVerificationAttemptGuard attemptGuard,
            QrVerificationMailClient mailClient,
            SecureRandom secureRandom,
            @Value("${app.email-verification.code-validity-minutes:15}") int codeValidityMinutes
    ) {
        this.merchantRepository = merchantRepository;
        this.passwordEncoder = passwordEncoder;
        this.attemptGuard = attemptGuard;
        this.mailClient = mailClient;
        this.secureRandom = secureRandom;
        this.codeValidityMinutes = codeValidityMinutes;
    }

    @AuthTransactional(readOnly = true)
    public Status status(Long merchantId) {
        return toStatus(loadMerchant(merchantId));
    }

    @AuthTransactional
    public Status request(Long merchantId) {
        MerchantEntity merchant = loadMerchant(merchantId);
        requireBasic(merchant);
        if (merchant.isEmailVerified()) {
            return toStatus(merchant);
        }
        sendWithCooldown(merchant);
        return toStatus(merchant);
    }

    @AuthTransactional
    public void resend(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        merchantRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email.trim().toLowerCase()).ifPresent(merchant -> {
            if (!"BASIC".equals(merchant.getProvider()) || merchant.isEmailVerified()) {
                return;
            }
            sendWithCooldown(merchant);
        });
    }

    @AuthTransactional
    public Status verify(Long merchantId, String code) {
        MerchantEntity merchant = loadMerchant(merchantId);
        requireBasic(merchant);
        apply(merchant, code);
        return toStatus(merchant);
    }

    @AuthTransactional
    public Status verifyByEmail(String email, String code) {
        MerchantEntity merchant = merchantRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(normalize(email))
                .orElseThrow(() -> new BadRequestException("Geçersiz doğrulama kodu"));
        requireBasic(merchant);
        if (merchant.isEmailVerified()) {
            return toStatus(merchant);
        }
        apply(merchant, code);
        return toStatus(merchant);
    }

    @AuthTransactional
    public void sendForAdmin(Long merchantId) {
        MerchantEntity merchant = loadMerchant(merchantId);
        requireBasic(merchant);
        if (merchant.isEmailVerified()) {
            throw new BadRequestException("E-posta zaten doğrulanmış");
        }
        issue(merchant);
    }

    @AuthTransactional
    public void reissue(Long merchantId, String email) {
        MerchantEntity merchant = loadMerchant(merchantId);
        requireBasic(merchant);
        String normalized = normalize(email);
        if (!normalized.equalsIgnoreCase(merchant.getEmail())
                && merchantRepository.existsByEmailIgnoreCaseAndDeletedAtIsNull(normalized)) {
            throw new BadRequestException("Bu e-posta adresi zaten kayıtlı");
        }
        merchant.setEmail(normalized);
        merchant.setEmailVerified(false);
        merchantRepository.save(merchant);
        issue(merchant);
    }

    @AuthTransactional
    public void issue(MerchantEntity merchant) {
        if (!"BASIC".equals(merchant.getProvider()) || merchant.isEmailVerified()) {
            return;
        }
        issueCode(merchant, LocalDateTime.now());
    }

    private void sendWithCooldown(MerchantEntity merchant) {
        LocalDateTime now = LocalDateTime.now();
        requireNotLocked(merchant, now);
        if (merchant.getEmailVerificationSentAt() != null
                && merchant.getEmailVerificationSentAt().plusMinutes(1).isAfter(now)) {
            throw new BadRequestException("Yeni doğrulama kodu için lütfen biraz bekleyin");
        }
        issueCode(merchant, now);
    }

    private void apply(MerchantEntity merchant, String rawCode) {
        LocalDateTime now = LocalDateTime.now();
        requireNotLocked(merchant, now);
        if (merchant.getEmailVerificationCodeHash() == null
                || merchant.getEmailVerificationExpiresAt() == null
                || !merchant.getEmailVerificationExpiresAt().isAfter(now)) {
            throw new BadRequestException("Doğrulama kodunun süresi dolmuş. Yeni kod isteyin.");
        }
        if (rawCode == null || !passwordEncoder.matches(rawCode.trim(), merchant.getEmailVerificationCodeHash())) {
            attemptGuard.registerFailure(merchant.getId());
            throw new BadRequestException("Geçersiz doğrulama kodu");
        }
        merchant.setEmailVerified(true);
        merchant.setEmailVerificationCodeHash(null);
        merchant.setEmailVerificationExpiresAt(null);
        attemptGuard.reset(merchant);
        merchantRepository.save(merchant);
    }

    private void issueCode(MerchantEntity merchant, LocalDateTime now) {
        String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        attemptGuard.reset(merchant);
        merchant.setEmailVerificationCodeHash(passwordEncoder.encode(code));
        merchant.setEmailVerificationExpiresAt(now.plusMinutes(codeValidityMinutes));
        merchant.setEmailVerificationSentAt(now);
        merchantRepository.save(merchant);
        String name = AuthSubjectMapper.displayName(merchant.getFirstName(), merchant.getLastName());
        mailClient.send(
                QrVerificationMailClient.EMAIL_VERIFICATION,
                merchant.getEmail(),
                name.isBlank() ? "Kullanıcı" : name,
                code,
                codeValidityMinutes
        );
    }

    private void requireNotLocked(MerchantEntity merchant, LocalDateTime now) {
        if (attemptGuard.isLocked(merchant, now)) {
            throw new BadRequestException(
                    "Çok fazla hatalı deneme yapıldı. Lütfen bir süre sonra yeni kod isteyin."
            );
        }
    }

    private MerchantEntity loadMerchant(Long merchantId) {
        return merchantRepository.findById(merchantId)
                .filter(merchant -> merchant.getDeletedAt() == null)
                .orElseThrow(() -> new NotFoundException("Kullanıcı bulunamadı"));
    }

    private static void requireBasic(MerchantEntity merchant) {
        if (!"BASIC".equals(merchant.getProvider())) {
            throw new BadRequestException("Bu hesap için e-posta doğrulaması gerekmiyor");
        }
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private static Status toStatus(MerchantEntity merchant) {
        return new Status(merchant.isEmailVerified(), merchant.getEmail(), merchant.getEmailVerificationExpiresAt());
    }

    public record Status(boolean verified, String email, LocalDateTime expiresAt) {
    }
}
