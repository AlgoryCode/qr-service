package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.dto.EmailVerificationDtos;
import com.ael.algoryqrservice.model.enums.AuthProvider;
import com.ael.algoryqrservice.repository.UserRepository;
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

    @Value("${app.email-verification.code-validity-minutes:15}")
    private int codeValidityMinutes;

    @Transactional(readOnly = true)
    public EmailVerificationDtos.Status status() {
        User user = securityUtils.getCurrentUser();
        return new EmailVerificationDtos.Status(user.isEmailVerified(), user.getEmail(), user.getEmailVerificationExpiresAt());
    }

    @Transactional
    public EmailVerificationDtos.Status requestCode() {
        User user = securityUtils.getCurrentUser();
        requireBasic(user);
        if (user.isEmailVerified()) {
            return status();
        }
        LocalDateTime now = LocalDateTime.now();
        if (user.getEmailVerificationSentAt() != null
                && user.getEmailVerificationSentAt().plusMinutes(1).isAfter(now)) {
            throw new BadRequestException("Yeni doğrulama kodu için lütfen biraz bekleyin");
        }
        issueCode(user, now);
        return status();
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
        LocalDateTime now = LocalDateTime.now();
        if (user.getEmailVerificationCodeHash() == null
                || user.getEmailVerificationExpiresAt() == null
                || !user.getEmailVerificationExpiresAt().isAfter(now)) {
            throw new BadRequestException("Doğrulama kodunun süresi dolmuş. Yeni kod isteyin.");
        }
        if (!passwordEncoder.matches(request.code().trim(), user.getEmailVerificationCodeHash())) {
            throw new BadRequestException("Geçersiz doğrulama kodu");
        }
        user.setEmailVerified(true);
        user.setEmailVerificationCodeHash(null);
        user.setEmailVerificationExpiresAt(null);
        userRepository.save(user);
        return status();
    }

    private void issueCode(User user, LocalDateTime now) {
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        user.setEmailVerificationCodeHash(passwordEncoder.encode(code));
        user.setEmailVerificationExpiresAt(now.plusMinutes(codeValidityMinutes));
        user.setEmailVerificationSentAt(now);
        userRepository.save(user);
        notificationPublisherService.publishEmailVerificationCode(
                user.getEmail(), user.getDisplayName().isBlank() ? "Kullanıcı" : user.getDisplayName(),
                code, codeValidityMinutes);
    }

    private void requireBasic(User user) {
        if (user.getProvider() != AuthProvider.BASIC) {
            throw new BadRequestException("Bu hesap için e-posta doğrulaması gerekmiyor");
        }
    }
}
