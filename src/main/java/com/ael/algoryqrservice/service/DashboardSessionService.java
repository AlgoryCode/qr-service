package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.UnauthorizedException;
import com.ael.algoryqrservice.model.DashboardUser;
import com.ael.algoryqrservice.model.DashboardUserSession;
import com.ael.algoryqrservice.model.dto.AuthResponse;
import com.ael.algoryqrservice.repository.DashboardUserRepository;
import com.ael.algoryqrservice.repository.DashboardUserSessionRepository;
import com.ael.algoryqrservice.util.ClientInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DashboardSessionService {

    private final DashboardUserSessionRepository sessionRepository;
    private final DashboardUserRepository dashboardUserRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final JwtPropertiesHelper jwtPropertiesHelper;

    @Transactional
    public AuthResponse createSession(DashboardUser user, ClientInfo clientInfo) {
        UUID sessionId = UUID.randomUUID();
        String rawRefreshToken = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();

        DashboardUserSession session = DashboardUserSession.builder()
                .id(sessionId)
                .dashboardUserId(user.getId())
                .refreshTokenHash(passwordEncoder.encode(rawRefreshToken))
                .loggedInAt(now)
                .accessExpiresAt(now.plus(jwtPropertiesHelper.getAccessDuration()))
                .refreshExpiresAt(now.plus(jwtPropertiesHelper.getRefreshDuration()))
                .lastActivityAt(now)
                .revoked(false)
                .ipAddress(clientInfo.ipAddress())
                .userAgent(clientInfo.userAgent())
                .device(clientInfo.device())
                .deviceType(clientInfo.deviceType())
                .build();

        sessionRepository.save(session);

        String accessToken = jwtService.generateDashboardAccessToken(
                user.getEmail(),
                sessionId,
                user.getId(),
                user.getRole()
        );
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(formatRefreshToken(sessionId, rawRefreshToken))
                .build();
    }

    @Transactional
    public AuthResponse refresh(String refreshToken) {
        RefreshTokenParts parts = parseRefreshToken(refreshToken);
        DashboardUserSession session = sessionRepository.findById(parts.sessionId())
                .orElseThrow(() -> new UnauthorizedException("Gecersiz refresh token"));
        validateSessionActive(session);
        if (!passwordEncoder.matches(parts.rawToken(), session.getRefreshTokenHash())) {
            throw new UnauthorizedException("Gecersiz refresh token");
        }

        DashboardUser user = dashboardUserRepository.findById(session.getDashboardUserId())
                .orElseThrow(() -> new UnauthorizedException("Kullanici bulunamadi"));
        if (!user.isActive()) {
            throw new UnauthorizedException("Hesap pasif");
        }

        String newRawRefreshToken = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        session.setRefreshTokenHash(passwordEncoder.encode(newRawRefreshToken));
        session.setAccessExpiresAt(now.plus(jwtPropertiesHelper.getAccessDuration()));
        session.setLastActivityAt(now);
        sessionRepository.save(session);

        String accessToken = jwtService.generateDashboardAccessToken(
                user.getEmail(),
                session.getId(),
                user.getId(),
                user.getRole()
        );
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(formatRefreshToken(session.getId(), newRawRefreshToken))
                .build();
    }

    @Transactional(readOnly = true)
    public boolean isSessionActive(UUID sessionId) {
        return sessionRepository.findById(sessionId)
                .map(DashboardUserSession::isActive)
                .orElse(false);
    }

    @Transactional
    public void revokeByAccessSessionId(UUID sessionId) {
        DashboardUserSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BadRequestException("Oturum bulunamadi"));
        revoke(session);
    }

    @Transactional
    public void revokeByRefreshToken(String refreshToken) {
        RefreshTokenParts parts = parseRefreshToken(refreshToken);
        DashboardUserSession session = sessionRepository.findById(parts.sessionId())
                .orElseThrow(() -> new UnauthorizedException("Gecersiz refresh token"));
        if (!passwordEncoder.matches(parts.rawToken(), session.getRefreshTokenHash())) {
            throw new UnauthorizedException("Gecersiz refresh token");
        }
        revoke(session);
    }

    private void validateSessionActive(DashboardUserSession session) {
        if (session.isRevoked()) {
            throw new UnauthorizedException("Oturum iptal edilmis");
        }
        if (session.isRefreshExpired()) {
            throw new UnauthorizedException("Oturum suresi dolmus");
        }
    }

    private void revoke(DashboardUserSession session) {
        if (!session.isRevoked()) {
            session.setRevoked(true);
            session.setRevokedAt(LocalDateTime.now());
            sessionRepository.save(session);
        }
    }

    private RefreshTokenParts parseRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BadRequestException("Refresh token zorunludur");
        }
        int separatorIndex = refreshToken.indexOf('.');
        if (separatorIndex <= 0 || separatorIndex == refreshToken.length() - 1) {
            throw new UnauthorizedException("Gecersiz refresh token formati");
        }
        try {
            UUID sessionId = UUID.fromString(refreshToken.substring(0, separatorIndex));
            String rawToken = refreshToken.substring(separatorIndex + 1);
            return new RefreshTokenParts(sessionId, rawToken);
        } catch (IllegalArgumentException e) {
            throw new UnauthorizedException("Gecersiz refresh token formati");
        }
    }

    private String formatRefreshToken(UUID sessionId, String rawToken) {
        return sessionId + "." + rawToken;
    }

    private record RefreshTokenParts(UUID sessionId, String rawToken) {
    }
}