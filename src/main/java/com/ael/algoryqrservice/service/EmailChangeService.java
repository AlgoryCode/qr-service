package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.EmailChangeChallenge;
import com.ael.algoryqrservice.model.EmailChangeCode;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.dto.AccountDtos;
import com.ael.algoryqrservice.model.enums.AuthProvider;
import com.ael.algoryqrservice.model.enums.EmailChangeChallengeStatus;
import com.ael.algoryqrservice.model.enums.EmailChangeCodePurpose;
import com.ael.algoryqrservice.model.enums.EmailChangeCodeStatus;
import com.ael.algoryqrservice.repository.EmailChangeChallengeRepository;
import com.ael.algoryqrservice.repository.EmailChangeCodeRepository;
import com.ael.algoryqrservice.repository.UserRepository;
import com.ael.algoryqrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailChangeService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final EnumSet<EmailChangeChallengeStatus> ACTIVE_STATUSES = EnumSet.of(
            EmailChangeChallengeStatus.AWAITING_CURRENT_CODE,
            EmailChangeChallengeStatus.CURRENT_VERIFIED,
            EmailChangeChallengeStatus.AWAITING_NEW_CODE
    );

    private final SecurityUtils securityUtils;
    private final UserRepository userRepository;
    private final EmailChangeChallengeRepository challengeRepository;
    private final EmailChangeCodeRepository codeRepository;
    private final NotificationPublisherService notificationPublisherService;

    @Value("${app.email-change.code-validity-minutes:5}")
    private int codeValidityMinutes;

    @Transactional
    public AccountDtos.EmailChangeCodeResponse requestCurrentCode() {
        User user = securityUtils.getCurrentUser();
        requireBasicProvider(user);
        LocalDateTime now = LocalDateTime.now();

        codeRepository.expireAllTimedOut(now);
        challengeRepository.cancelActiveByUserId(user.getId(), ACTIVE_STATUSES);
        codeRepository.revokePendingByUserId(user.getId(), now);

        EmailChangeChallenge challenge = EmailChangeChallenge.builder()
                .id(UUID.randomUUID())
                .userId(user.getId())
                .currentEmail(user.getEmail())
                .status(EmailChangeChallengeStatus.AWAITING_CURRENT_CODE)
                .createdAt(now)
                .build();
        challengeRepository.save(challenge);

        createAndSendCode(
                challenge.getId(),
                user,
                user.getEmail(),
                EmailChangeCodePurpose.CURRENT_EMAIL,
                now
        );

        return toCodeResponse(challenge.getId(), user.getEmail());
    }

    @Transactional
    public AccountDtos.EmailChangeCodeResponse verifyCurrent(AccountDtos.EmailChangeVerifyCurrentRequest request) {
        User user = securityUtils.getCurrentUser();
        requireBasicProvider(user);
        LocalDateTime now = LocalDateTime.now();
        codeRepository.expireAllTimedOut(now);

        EmailChangeChallenge challenge = loadOwnedChallenge(request.getChallengeId(), user.getId());
        if (challenge.getStatus() != EmailChangeChallengeStatus.AWAITING_CURRENT_CODE) {
            throw new BadRequestException("Bu adım için geçerli bir işlem bulunamadı. Sürece yeniden başlayın.");
        }

        consumeCode(challenge.getId(), EmailChangeCodePurpose.CURRENT_EMAIL, request.getCode(), now);

        challenge = loadOwnedChallenge(request.getChallengeId(), user.getId());
        challenge.setStatus(EmailChangeChallengeStatus.CURRENT_VERIFIED);
        challenge.setCurrentVerifiedAt(now);
        challengeRepository.saveAndFlush(challenge);

        return AccountDtos.EmailChangeCodeResponse.builder()
                .challengeId(challenge.getId().toString())
                .maskedEmail(maskEmail(challenge.getCurrentEmail()))
                .expiresInSeconds(0)
                .validityMinutes(codeValidityMinutes)
                .build();
    }

    @Transactional
    public AccountDtos.EmailChangeCodeResponse requestNewCode(AccountDtos.EmailChangeRequestNewCodeRequest request) {
        User user = securityUtils.getCurrentUser();
        requireBasicProvider(user);
        LocalDateTime now = LocalDateTime.now();
        codeRepository.expireAllTimedOut(now);

        EmailChangeChallenge challenge = loadOwnedChallenge(request.getChallengeId(), user.getId());
        if (challenge.getStatus() != EmailChangeChallengeStatus.CURRENT_VERIFIED
                && challenge.getStatus() != EmailChangeChallengeStatus.AWAITING_NEW_CODE) {
            throw new BadRequestException("Önce mevcut e-posta adresinizi doğrulamanız gerekir.");
        }

        String newEmail = request.getNewEmail().trim().toLowerCase();
        if (newEmail.equalsIgnoreCase(user.getEmail())) {
            throw new BadRequestException("Yeni e-posta mevcut e-posta ile aynı olamaz");
        }
        if (userRepository.existsByEmailAndIdNot(newEmail, user.getId())) {
            throw new BadRequestException("Bu e-posta adresi zaten kayıtlı");
        }

        challenge.setNewEmail(newEmail);
        challenge.setStatus(EmailChangeChallengeStatus.AWAITING_NEW_CODE);
        challengeRepository.saveAndFlush(challenge);

        codeRepository.revokePendingByChallengeAndPurpose(challenge.getId(), EmailChangeCodePurpose.NEW_EMAIL, now);
        createAndSendCode(challenge.getId(), user, newEmail, EmailChangeCodePurpose.NEW_EMAIL, now);

        return toCodeResponse(challenge.getId(), newEmail);
    }

    @Transactional
    public void confirm(AccountDtos.EmailChangeConfirmRequest request) {
        User user = securityUtils.getCurrentUser();
        requireBasicProvider(user);
        LocalDateTime now = LocalDateTime.now();
        codeRepository.expireAllTimedOut(now);

        EmailChangeChallenge challenge = loadOwnedChallenge(request.getChallengeId(), user.getId());
        if (challenge.getStatus() != EmailChangeChallengeStatus.AWAITING_NEW_CODE) {
            throw new BadRequestException("Yeni e-posta doğrulaması için geçerli bir işlem bulunamadı.");
        }
        if (challenge.getNewEmail() == null || challenge.getNewEmail().isBlank()) {
            throw new BadRequestException("Yeni e-posta bilgisi eksik. Sürece yeniden başlayın.");
        }

        consumeCode(challenge.getId(), EmailChangeCodePurpose.NEW_EMAIL, request.getCode(), now);

        challenge = loadOwnedChallenge(request.getChallengeId(), user.getId());
        if (userRepository.existsByEmailAndIdNot(challenge.getNewEmail(), user.getId())) {
            throw new BadRequestException("Bu e-posta adresi zaten kayıtlı");
        }

        user.setEmail(challenge.getNewEmail());
        userRepository.save(user);

        challenge.setStatus(EmailChangeChallengeStatus.COMPLETED);
        challenge.setCompletedAt(now);
        challengeRepository.saveAndFlush(challenge);
    }

    private void createAndSendCode(
            UUID challengeId,
            User user,
            String email,
            EmailChangeCodePurpose purpose,
            LocalDateTime now
    ) {
        String code = generateSixDigitCode();
        EmailChangeCode entity = EmailChangeCode.builder()
                .challengeId(challengeId)
                .userId(user.getId())
                .email(email)
                .code(code)
                .purpose(purpose)
                .status(EmailChangeCodeStatus.PENDING)
                .revoked(false)
                .createdAt(now)
                .expiresAt(now.plusMinutes(codeValidityMinutes))
                .build();
        codeRepository.save(entity);

        String userName = (user.getFirstName() + " " + user.getLastName()).trim();
        try {
            notificationPublisherService.publishEmailChangeCode(
                    email,
                    userName.isBlank() ? "Kullanıcı" : userName,
                    code,
                    codeValidityMinutes
            );
        } catch (Exception exception) {
            log.error("Email change code notification failed. userId={}, purpose={}", user.getId(), purpose, exception);
            throw new BadRequestException("Doğrulama kodu gönderilemedi. Lütfen daha sonra tekrar deneyin.");
        }
    }

    private void consumeCode(
            UUID challengeId,
            EmailChangeCodePurpose purpose,
            String rawCode,
            LocalDateTime now
    ) {
        String code = rawCode.trim();
        EmailChangeCode changeCode = codeRepository
                .findFirstByChallengeIdAndPurposeAndCodeAndStatusAndRevokedFalseOrderByCreatedAtDesc(
                        challengeId,
                        purpose,
                        code,
                        EmailChangeCodeStatus.PENDING
                )
                .orElseThrow(() -> new BadRequestException("Geçersiz doğrulama kodu"));

        if (changeCode.isExpired(now)) {
            changeCode.setStatus(EmailChangeCodeStatus.EXPIRED);
            codeRepository.save(changeCode);
            throw new BadRequestException("Doğrulama kodunun süresi dolmuş. Lütfen yeni kod isteyin.");
        }

        changeCode.setStatus(EmailChangeCodeStatus.USED);
        changeCode.setUsedAt(now);
        codeRepository.save(changeCode);
        codeRepository.revokePendingByChallengeAndPurpose(challengeId, purpose, now);
    }

    private EmailChangeChallenge loadOwnedChallenge(String challengeId, Long userId) {
        UUID id;
        try {
            id = UUID.fromString(challengeId.trim());
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Geçersiz işlem kimliği");
        }
        return challengeRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BadRequestException("İşlem bulunamadı. Sürece yeniden başlayın."));
    }

    private AccountDtos.EmailChangeCodeResponse toCodeResponse(UUID challengeId, String email) {
        return AccountDtos.EmailChangeCodeResponse.builder()
                .challengeId(challengeId.toString())
                .maskedEmail(maskEmail(email))
                .expiresInSeconds(codeValidityMinutes * 60)
                .validityMinutes(codeValidityMinutes)
                .build();
    }

    private String generateSixDigitCode() {
        return String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
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
            throw new BadRequestException("Google hesabının e-posta adresi uygulama içinden değiştirilemez");
        }
    }
}
