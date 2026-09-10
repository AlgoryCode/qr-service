package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.NotFoundException;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.dto.AdminUserDtos;
import com.ael.algoryqrservice.model.enums.AuthProvider;
import com.ael.algoryqrservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserCredentialService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String PASSWORD_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final int PASSWORD_LENGTH = 12;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessionService;
    private final NotificationPublisherService notificationPublisherService;
    private final EmailVerificationService emailVerificationService;

    @Transactional
    public void sendEmailVerification(Long userId) {
        User user = loadUser(userId);
        emailVerificationService.sendForAdmin(user);
    }

    @Transactional
    public AdminUserDtos.PasswordResetResponse resetPassword(Long userId) {
        User user = loadUser(userId);
        if (user.getProvider() != AuthProvider.BASIC) {
            throw new BadRequestException("Google hesabı için parola işlemi yapılamaz");
        }

        String temporaryPassword = generatePassword();
        user.setPassword(passwordEncoder.encode(temporaryPassword));
        userRepository.save(user);
        sessionService.revokeAllActiveSessions(user.getId());

        boolean emailed = false;
        if (user.isEmailVerified()) {
            emailed = sendTemporaryPasswordMail(user, temporaryPassword);
        }

        return AdminUserDtos.PasswordResetResponse.builder()
                .temporaryPassword(temporaryPassword)
                .emailed(emailed)
                .build();
    }

    private boolean sendTemporaryPasswordMail(User user, String temporaryPassword) {
        String userName = user.getDisplayName();
        try {
            notificationPublisherService.publishTemporaryPassword(
                    user.getEmail(),
                    userName.isBlank() ? "Kullanıcı" : userName,
                    temporaryPassword
            );
            return true;
        } catch (Exception exception) {
            log.error("Temporary password notification failed. userId={}", user.getId(), exception);
            return false;
        }
    }

    private User loadUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Kullanıcı bulunamadı"));
    }

    private String generatePassword() {
        StringBuilder builder = new StringBuilder(PASSWORD_LENGTH);
        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            builder.append(PASSWORD_ALPHABET.charAt(SECURE_RANDOM.nextInt(PASSWORD_ALPHABET.length())));
        }
        return builder.toString();
    }
}
