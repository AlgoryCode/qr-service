package com.ael.algoryqrservice.adminservice.application;

import com.ael.algoryqrservice.adminservice.dto.DashboardAuthDtos.CreateDashboardUserRequest;
import com.ael.algoryqrservice.adminservice.dto.DashboardAuthDtos.MfaSetupResponse;
import com.ael.algoryqrservice.adminservice.dto.DashboardAuthDtos.UpdateDashboardUserRequest;
import com.ael.algoryqrservice.adminservice.infrastructure.persistence.entity.DashboardUser;
import com.ael.algoryqrservice.adminservice.infrastructure.persistence.entity.enums.DashboardRole;
import com.ael.algoryqrservice.adminservice.infrastructure.persistence.repository.DashboardUserRepository;
import com.ael.algoryqrservice.adminservice.security.Totp;
import com.ael.algoryqrservice.qrservice.exception.BadRequestException;
import com.ael.algoryqrservice.qrservice.exception.ConflictException;
import com.ael.algoryqrservice.qrservice.exception.NotFoundException;
import com.ael.algoryqrservice.qrservice.util.AppTime;
import com.ael.algoryqrservice.qrservice.util.QrCodeGeneratorUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Admin panel accounts: who can sign in, with which role, and their own password and second factor. */
@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardAccountService {

    static final String TOTP_ISSUER = "AlgoryQR Admin";

    private final DashboardUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final DashboardSessionService sessionService;
    private final QrCodeGeneratorUtil qrCodeGenerator;

    @Transactional(readOnly = true)
    public List<DashboardUser> list() {
        return repository.findAll().stream()
                .sorted(Comparator.comparing(DashboardUser::getId))
                .toList();
    }

    @Transactional
    public DashboardUser create(CreateDashboardUserRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (repository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("DASHBOARD_USER_EXISTS", "Bu e-posta ile bir hesap var");
        }
        String username = normalizeUsername(request.username());
        if (username != null && repository.findByUsernameIgnoreCase(username).isPresent()) {
            throw new ConflictException("DASHBOARD_USERNAME_TAKEN", "Bu kullanıcı adı kullanılıyor");
        }
        DashboardUser user = repository.save(DashboardUser.builder()
                .email(email)
                .username(username)
                .firstName(request.firstName().trim())
                .lastName(request.lastName().trim())
                .role(request.role())
                .active(true)
                .password(passwordEncoder.encode(request.password()))
                .build());
        log.info("dashboard_user_created id={} role={}", user.getId(), user.getRole());
        return user;
    }

    @Transactional
    public DashboardUser update(Long id, UpdateDashboardUserRequest request, Long actingUserId) {
        DashboardUser user = get(id);
        boolean signOut = false;
        if (request.firstName() != null && !request.firstName().isBlank()) {
            user.setFirstName(request.firstName().trim());
        }
        if (request.lastName() != null && !request.lastName().isBlank()) {
            user.setLastName(request.lastName().trim());
        }
        if (request.username() != null) {
            String username = normalizeUsername(request.username());
            if (username != null && repository.findByUsernameIgnoreCase(username)
                    .filter(other -> !other.getId().equals(id)).isPresent()) {
                throw new ConflictException("DASHBOARD_USERNAME_TAKEN", "Bu kullanıcı adı kullanılıyor");
            }
            user.setUsername(username);
        }
        if (request.role() != null && request.role() != user.getRole()) {
            if (user.getRole() == DashboardRole.ADMIN) {
                requireAnotherActiveAdmin(user);
            }
            user.setRole(request.role());
            signOut = true;
        }
        if (request.active() != null && request.active() != user.isActive()) {
            if (!request.active()) {
                if (id.equals(actingUserId)) {
                    throw new BadRequestException("Kendi hesabınızı pasif yapamazsınız");
                }
                if (user.getRole() == DashboardRole.ADMIN) {
                    requireAnotherActiveAdmin(user);
                }
                signOut = true;
            }
            user.setActive(request.active());
        }
        repository.save(user);
        if (signOut) {
            sessionService.revokeAll(id);
        }
        return user;
    }

    /** Sets a new password chosen by a super admin and signs the account out everywhere. */
    @Transactional
    public void setPassword(Long id, String password) {
        DashboardUser user = get(id);
        user.setPassword(passwordEncoder.encode(password));
        repository.save(user);
        sessionService.revokeAll(id);
    }

    /** For a lost phone: turns the second factor off so the account can set it up again. */
    @Transactional
    public void resetMfa(Long id) {
        DashboardUser user = get(id);
        user.setTotpEnabled(false);
        user.setTotpSecret(null);
        repository.save(user);
        sessionService.revokeAll(id);
    }

    @Transactional
    public void changeOwnPassword(Long id, String currentPassword, String newPassword) {
        DashboardUser user = get(id);
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new BadRequestException("Mevcut şifre hatalı");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        repository.save(user);
    }

    /** Starts (or restarts) two-factor setup; the factor is on only after {@link #enableMfa}. */
    @Transactional
    public MfaSetupResponse startMfaSetup(Long id) {
        DashboardUser user = get(id);
        if (user.isTotpEnabled()) {
            throw new ConflictException("MFA_ALREADY_ENABLED", "İki adımlı doğrulama zaten açık");
        }
        String secret = Totp.newSecret();
        user.setTotpSecret(secret);
        repository.save(user);
        String account = user.getUsername() != null ? user.getUsername() : user.getEmail();
        String uri = Totp.otpauthUri(TOTP_ISSUER, account, secret);
        String png;
        try {
            png = qrCodeGenerator.generateBase64Png(uri);
        } catch (Exception e) {
            png = null;
        }
        return new MfaSetupResponse(secret, uri, png);
    }

    @Transactional
    public void enableMfa(Long id, String code) {
        DashboardUser user = get(id);
        if (user.getTotpSecret() == null) {
            throw new BadRequestException("Önce kurulumu başlatın");
        }
        if (!Totp.verify(user.getTotpSecret(), code, AppTime.clock().instant().getEpochSecond())) {
            throw new BadRequestException("Kod hatalı; uygulamadaki güncel kodu girin");
        }
        user.setTotpEnabled(true);
        repository.save(user);
    }

    @Transactional
    public void disableMfa(Long id, String code) {
        DashboardUser user = get(id);
        if (!user.isTotpEnabled()) {
            return;
        }
        if (!Totp.verify(user.getTotpSecret(), code, AppTime.clock().instant().getEpochSecond())) {
            throw new BadRequestException("Kod hatalı");
        }
        user.setTotpEnabled(false);
        user.setTotpSecret(null);
        repository.save(user);
    }

    private DashboardUser get(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Hesap bulunamadı"));
    }

    private void requireAnotherActiveAdmin(DashboardUser leaving) {
        boolean another = repository.findAll().stream()
                .anyMatch(u -> !u.getId().equals(leaving.getId()) && u.isActive() && u.getRole() == DashboardRole.ADMIN);
        if (!another) {
            throw new BadRequestException("En az bir aktif süper admin kalmalı");
        }
    }

    private static String normalizeUsername(String username) {
        return username == null || username.isBlank() ? null : username.trim().toLowerCase(Locale.ROOT);
    }
}
