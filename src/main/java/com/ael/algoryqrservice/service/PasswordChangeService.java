package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.PasswordChangeCode;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.dto.AccountDtos;
import com.ael.algoryqrservice.model.enums.AuthProvider;
import com.ael.algoryqrservice.model.enums.PasswordChangeCodeStatus;
import com.ael.algoryqrservice.repository.PasswordChangeCodeRepository;
import com.ael.algoryqrservice.repository.UserRepository;
import com.ael.algoryqrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordChangeService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final SecurityUtils securityUtils;
    private final UserRepository userRepository;
    private final PasswordChangeCodeRepository passwordChangeCodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationPublisherService notificationPublisherService;
    private final SessionService sessionService;

    @Value("${app.password-change.code-validity-minutes:5}")
    private int codeValidityMinutes;

    @Transactional
    public AccountDtos.PasswordChangeCodeResponse requestCode() {
        User user = securityUtils.getCurrentUser();
        requireBasicProvider(user);
        LocalDateTime now = LocalDateTime.now();

        passwordChangeCodeRepository.expireAllTimedOut(now);
        passwordChangeCodeRepository.revokePendingByUserId(user.getId(), now);

        String code = generateSixDigitCode();
        PasswordChangeCode entity = PasswordChangeCode.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .code(code)
                .status(PasswordChangeCodeStatus.PENDING)
                .revoked(false)
                .createdAt(now)
                .expiresAt(now.plusMinutes(codeValidityMinutes))
                .build();
        passwordChangeCodeRepository.save(entity);

        String userName = (user.getFirstName() + " " + user.getLastName()).trim();
        try {
            notificationPublisherService.publishPasswordChangeCode(
                    user.getEmail(),
                    userName.isBlank() ? "Kullanıcı" : userName,
                    code,
                    codeValidityMinutes
            );
        } catch (Exception exception) {
            log.error("Password change code notification failed. userId={}", user.getId(), exception);
            throw new BadRequestException("Doğrulama kodu gönderilemedi. Lütfen daha sonra tekrar deneyin.");
        }

        return AccountDtos.PasswordChangeCodeResponse.builder()
                .maskedEmail(maskEmail(user.getEmail()))
                .expiresInSeconds(codeValidityMinutes * 60)
                .validityMinutes(codeValidityMinutes)
                .build();
    }

    @Transactional
    public void confirm(AccountDtos.ConfirmPasswordChangeRequest request) {
        User user = securityUtils.getCurrentUser();
        requireBasicProvider(user);
        LocalDateTime now = LocalDateTime.now();

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("Yeni şifre ile tekrarı eşleşmiyor");
        }

        passwordChangeCodeRepository.expireAllTimedOut(now);

        String code = request.getCode().trim();
        PasswordChangeCode changeCode = passwordChangeCodeRepository
                .findFirstByUserIdAndCodeAndStatusAndRevokedFalseOrderByCreatedAtDesc(
                        user.getId(),
                        code,
                        PasswordChangeCodeStatus.PENDING
                )
                .orElseThrow(() -> new BadRequestException("Geçersiz doğrulama kodu"));

        if (changeCode.isExpired(now)) {
            changeCode.setStatus(PasswordChangeCodeStatus.EXPIRED);
            passwordChangeCodeRepository.save(changeCode);
            throw new BadRequestException("Doğrulama kodunun süresi dolmuş. Lütfen yeni kod isteyin.");
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new BadRequestException("Yeni şifre mevcut şifre ile aynı olamaz");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        changeCode.setStatus(PasswordChangeCodeStatus.USED);
        changeCode.setUsedAt(now);
        passwordChangeCodeRepository.save(changeCode);

        passwordChangeCodeRepository.revokePendingByUserId(user.getId(), now);
        sessionService.revokeAllActiveSessions(user.getId());
    }

    private String generateSixDigitCode() {
        int value = SECURE_RANDOM.nextInt(1_000_000);
        return String.format("%06d", value);
    }

    private String maskEmail(String email) {
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

    private void requireBasicProvider(User user) {
        if (user.getProvider() != AuthProvider.BASIC) {
            throw new BadRequestException("Google hesabı için parola işlemi yapılamaz");
        }
    }
}
