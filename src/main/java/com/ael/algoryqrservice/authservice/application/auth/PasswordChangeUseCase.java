package com.ael.algoryqrservice.authservice.application.auth;

import com.ael.algoryqrservice.authservice.exception.BadRequestException;
import com.ael.algoryqrservice.authservice.exception.NotFoundException;
import com.ael.algoryqrservice.authservice.infrastructure.client.QrVerificationMailClient;
import com.ael.algoryqrservice.authservice.infrastructure.persistence.entity.DemoAccountEntity;
import com.ael.algoryqrservice.authservice.infrastructure.persistence.entity.MerchantEntity;
import com.ael.algoryqrservice.authservice.infrastructure.persistence.entity.PasswordChangeCodeEntity;
import com.ael.algoryqrservice.authservice.infrastructure.persistence.entity.SessionEntity;
import com.ael.algoryqrservice.authservice.infrastructure.persistence.repository.DemoAccountJpaRepository;
import com.ael.algoryqrservice.authservice.infrastructure.persistence.repository.MerchantJpaRepository;
import com.ael.algoryqrservice.authservice.infrastructure.persistence.repository.PasswordChangeCodeJpaRepository;
import com.ael.algoryqrservice.authservice.infrastructure.persistence.repository.SessionJpaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.ael.algoryqrservice.authservice.config.AuthTransactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class PasswordChangeUseCase {

    private final MerchantJpaRepository merchantRepository;
    private final DemoAccountJpaRepository demoAccountRepository;
    private final PasswordChangeCodeJpaRepository codeRepository;
    private final SessionJpaRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final QrVerificationMailClient mailClient;
    private final SecureRandom secureRandom;
    private final int codeValidityMinutes;

    public PasswordChangeUseCase(
            MerchantJpaRepository merchantRepository,
            DemoAccountJpaRepository demoAccountRepository,
            PasswordChangeCodeJpaRepository codeRepository,
            SessionJpaRepository sessionRepository,
            PasswordEncoder passwordEncoder,
            QrVerificationMailClient mailClient,
            SecureRandom secureRandom,
            @Value("${app.password-change.code-validity-minutes:5}") int codeValidityMinutes
    ) {
        this.merchantRepository = merchantRepository;
        this.demoAccountRepository = demoAccountRepository;
        this.codeRepository = codeRepository;
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailClient = mailClient;
        this.secureRandom = secureRandom;
        this.codeValidityMinutes = codeValidityMinutes;
    }

    @AuthTransactional
    public CodeResponse request(Long merchantId) {
        MerchantEntity merchant = loadBasic(merchantId);
        LocalDateTime now = LocalDateTime.now();
        expireTimedOut(merchantId, now);
        revokePending(merchantId, now);

        String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        PasswordChangeCodeEntity entity = new PasswordChangeCodeEntity();
        entity.setMerchantId(merchant.getId());
        entity.setEmail(merchant.getEmail());
        entity.setCodeHash(passwordEncoder.encode(code));
        entity.setStatus(PasswordChangeCodeEntity.PENDING);
        entity.setRevoked(false);
        entity.setCreatedAt(now);
        entity.setExpiresAt(now.plusMinutes(codeValidityMinutes));
        codeRepository.save(entity);

        String name = AuthSubjectMapper.displayName(merchant.getFirstName(), merchant.getLastName());
        mailClient.send(
                QrVerificationMailClient.PASSWORD_CHANGE,
                merchant.getEmail(),
                name.isBlank() ? "Kullanıcı" : name,
                code,
                codeValidityMinutes
        );
        return new CodeResponse(maskEmail(merchant.getEmail()), codeValidityMinutes * 60, codeValidityMinutes);
    }

    @AuthTransactional
    public void confirm(Long merchantId, String code, String newPassword, String confirmPassword) {
        MerchantEntity merchant = loadBasic(merchantId);
        LocalDateTime now = LocalDateTime.now();
        if (newPassword == null || confirmPassword == null || !newPassword.equals(confirmPassword)) {
            throw new BadRequestException("Yeni şifre ile tekrarı eşleşmiyor");
        }
        if (newPassword.length() < 8) {
            throw new BadRequestException("Yeni şifre en az 8 karakter olmalıdır");
        }
        expireTimedOut(merchantId, now);
        PasswordChangeCodeEntity changeCode = matchPending(merchantId, code, now);
        DemoAccountEntity demoAccount = demoAccountRepository.findByUserId(merchant.getId()).orElse(null);
        String currentPassword = demoAccount != null ? demoAccount.getPassword() : merchant.getPassword();
        if (currentPassword != null && passwordEncoder.matches(newPassword, currentPassword)) {
            throw new BadRequestException("Yeni şifre mevcut şifre ile aynı olamaz");
        }
        String encodedPassword = passwordEncoder.encode(newPassword);
        if (demoAccount != null) {
            demoAccount.setPassword(encodedPassword);
            demoAccountRepository.save(demoAccount);
            merchant.setPassword(null);
        } else {
            merchant.setPassword(encodedPassword);
        }
        merchantRepository.save(merchant);
        changeCode.setStatus(PasswordChangeCodeEntity.USED);
        changeCode.setUsedAt(now);
        codeRepository.save(changeCode);
        revokePending(merchantId, now);
        revokeSessions(merchantId, now);
    }

    private PasswordChangeCodeEntity matchPending(Long merchantId, String rawCode, LocalDateTime now) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new BadRequestException("Geçersiz doğrulama kodu");
        }
        String code = rawCode.trim();
        List<PasswordChangeCodeEntity> pending = codeRepository
                .findByMerchantIdAndStatusAndRevokedFalseOrderByCreatedAtDesc(
                        merchantId,
                        PasswordChangeCodeEntity.PENDING
                );
        boolean matchedExpired = false;
        for (PasswordChangeCodeEntity candidate : pending) {
            boolean matches = passwordEncoder.matches(code, candidate.getCodeHash());
            if (!candidate.getExpiresAt().isAfter(now)) {
                candidate.setStatus(PasswordChangeCodeEntity.EXPIRED);
                codeRepository.save(candidate);
                matchedExpired = matchedExpired || matches;
                continue;
            }
            if (matches) {
                return candidate;
            }
        }
        if (matchedExpired) {
            throw new BadRequestException("Doğrulama kodunun süresi dolmuş. Lütfen yeni kod isteyin.");
        }
        throw new BadRequestException("Geçersiz doğrulama kodu");
    }

    private void expireTimedOut(Long merchantId, LocalDateTime now) {
        for (PasswordChangeCodeEntity candidate : codeRepository
                .findByMerchantIdAndStatusAndRevokedFalseOrderByCreatedAtDesc(
                        merchantId,
                        PasswordChangeCodeEntity.PENDING
                )) {
            if (!candidate.getExpiresAt().isAfter(now)) {
                candidate.setStatus(PasswordChangeCodeEntity.EXPIRED);
                codeRepository.save(candidate);
            }
        }
    }

    private void revokePending(Long merchantId, LocalDateTime now) {
        for (PasswordChangeCodeEntity candidate : codeRepository
                .findByMerchantIdAndStatusAndRevokedFalseOrderByCreatedAtDesc(
                        merchantId,
                        PasswordChangeCodeEntity.PENDING
                )) {
            candidate.setRevoked(true);
            candidate.setRevokedAt(now);
            codeRepository.save(candidate);
        }
    }

    private void revokeSessions(Long merchantId, LocalDateTime now) {
        for (SessionEntity session : sessionRepository.findBySubjectTypeAndSubjectIdAndRevokedFalse(
                AuthSubject.TYPE_MERCHANT,
                merchantId
        )) {
            session.setRevoked(true);
            session.setLastActivityAt(now);
        }
    }

    private MerchantEntity loadBasic(Long merchantId) {
        MerchantEntity merchant = merchantRepository.findById(merchantId)
                .filter(found -> found.getDeletedAt() == null)
                .orElseThrow(() -> new NotFoundException("Kullanıcı bulunamadı"));
        if (!"BASIC".equals(merchant.getProvider())) {
            throw new BadRequestException("Google hesabı için parola işlemi yapılamaz");
        }
        return merchant;
    }

    private static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        String domain = email.substring(at);
        if (local.length() <= 2) {
            return "*".repeat(Math.max(local.length(), 1)) + domain;
        }
        return local.charAt(0) + "***" + local.charAt(local.length() - 1) + domain;
    }

    public record CodeResponse(String maskedEmail, int expiresInSeconds, int validityMinutes) {
    }
}
