package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.dto.EmailVerificationDtos;
import com.ael.algoryqrservice.model.enums.AuthProvider;
import com.ael.algoryqrservice.repository.UserRepository;
import com.ael.algoryqrservice.security.EmailVerificationGate;
import com.ael.algoryqrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecurityUtils securityUtils;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationPublisherService notificationPublisherService;
    private final EmailVerificationAttemptGuard attemptGuard;
    private final EmailVerificationGate emailVerificationGate;

    @Value("${app.email-verification.code-validity-minutes:15}")
    private int codeValidityMinutes;

    @Transactional(readOnly = true)
    public EmailVerificationDtos.Status status() {
        User user = securityUtils.getCurrentUser();
        return toStatus(user);
    }

    @Transactional
    public EmailVerificationDtos.Status requestCode() {
        User user = securityUtils.getCurrentUser();
        requireBasic(user);
        if (user.isEmailVerified()) {
            return toStatus(user);
        }
        sendCodeWithCooldown(user);
        return toStatus(user);
    }

    @Transactional
    public void resendByEmail(EmailVerificationDtos.ResendByEmailRequest request) {
        String email = request.email().trim().toLowerCase();
        userRepository.findByEmail(email).ifPresent(user -> {
            if (user.getProvider() != AuthProvider.BASIC || user.isEmailVerified()) {
                return;
            }
            sendCodeWithCooldown(user);
        });
    }

    @Transactional
    public void sendForUser(User user) {
        if (user.getProvider() == AuthProvider.BASIC && !user.isEmailVerified()) {
            issueCode(user, LocalDateTime.now());
        }
    }

    @Transactional
    public EmailVerificationDtos.Status verify(EmailVerificationDtos.VerifyRequest request) {
        User user = securityUtils.getCurrentUser();
        requireBasic(user);
        applyVerification(user, request.code());
        return toStatus(user);
    }

    @Transactional
    public EmailVerificationDtos.Status verifyByEmail(EmailVerificationDtos.PublicVerifyRequest request) {
        String email = request.email().trim().toLowerCase();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("Geçersiz doğrulama kodu"));
        requireBasic(user);
        if (user.isEmailVerified()) {
            return toStatus(user);
        }
        applyVerification(user, request.code());
        return toStatus(user);
    }

    @Transactional
    public void sendForAdmin(User user) {
        requireBasic(user);
        if (user.isEmailVerified()) {
            throw new BadRequestException("E-posta zaten doğrulanmış");
        }
        issueCode(user, LocalDateTime.now());
    }

    private void sendCodeWithCooldown(User user) {
        LocalDateTime now = LocalDateTime.now();
        requireNotLocked(user, now);
        if (user.getEmailVerificationSentAt() != null
                && user.getEmailVerificationSentAt().plusMinutes(1).isAfter(now)) {
            throw new BadRequestException("Yeni doğrulama kodu için lütfen biraz bekleyin");
        }
        issueCode(user, now);
    }

    private void applyVerification(User user, String rawCode) {
        LocalDateTime now = LocalDateTime.now();
        requireNotLocked(user, now);
        if (user.getEmailVerificationCodeHash() == null
                || user.getEmailVerificationExpiresAt() == null
                || !user.getEmailVerificationExpiresAt().isAfter(now)) {
            throw new BadRequestException("Doğrulama kodunun süresi dolmuş. Yeni kod isteyin.");
        }
        if (!passwordEncoder.matches(rawCode.trim(), user.getEmailVerificationCodeHash())) {
            attemptGuard.registerFailure(user.getId());
            throw new BadRequestException("Geçersiz doğrulama kodu");
        }
        user.setEmailVerified(true);
        user.setEmailVerificationCodeHash(null);
        user.setEmailVerificationExpiresAt(null);
        attemptGuard.reset(user);
        userRepository.save(user);
        emailVerificationGate.markVerified(user.getId());
    }

    private void requireNotLocked(User user, LocalDateTime now) {
        if (attemptGuard.isLocked(user, now)) {
            throw new BadRequestException(
                    "Çok fazla hatalı deneme yapıldı. Lütfen bir süre sonra yeni kod isteyin."
            );
        }
    }

    private void issueCode(User user, LocalDateTime now) {
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        attemptGuard.reset(user);
        user.setEmailVerificationCodeHash(passwordEncoder.encode(code));
        user.setEmailVerificationExpiresAt(now.plusMinutes(codeValidityMinutes));
        user.setEmailVerificationSentAt(now);
        userRepository.save(user);
        notificationPublisherService.publishEmailVerificationCode(
                user.getEmail(), user.getDisplayName().isBlank() ? "Kullanıcı" : user.getDisplayName(),
                code, codeValidityMinutes);
    }

    private EmailVerificationDtos.Status toStatus(User user) {
        return new EmailVerificationDtos.Status(
                user.isEmailVerified(),
                user.getEmail(),
                user.getEmailVerificationExpiresAt()
        );
    }

    private void requireBasic(User user) {
        if (user.getProvider() != AuthProvider.BASIC) {
            throw new BadRequestException("Bu hesap için e-posta doğrulaması gerekmiyor");
        }
    }
}
