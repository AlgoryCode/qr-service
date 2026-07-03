package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.UnauthorizedException;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.UserSession;
import com.ael.algoryqrservice.model.dto.AuthResponse;
import com.ael.algoryqrservice.model.dto.SessionResponse;
import com.ael.algoryqrservice.repository.UserRepository;
import com.ael.algoryqrservice.repository.UserSessionRepository;
import com.ael.algoryqrservice.util.ClientInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SessionService {

    private final UserSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final JwtPropertiesHelper jwtPropertiesHelper;

    @Transactional
    public SessionTokens createSession(User user, ClientInfo clientInfo) {
        UUID sessionId = UUID.randomUUID();
        String rawRefreshToken = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();

        UserSession session = UserSession.builder()
                .id(sessionId)
                .userId(user.getId())
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

        String accessToken = jwtService.generateAccessToken(user.getEmail(), sessionId);
        String refreshToken = formatRefreshToken(sessionId, rawRefreshToken);

        return new SessionTokens(session, accessToken, refreshToken, user);
    }

    @Transactional
    public AuthResponse refresh(String refreshToken) {
        RefreshTokenParts parts = parseRefreshToken(refreshToken);

        UserSession session = sessionRepository.findById(parts.sessionId())
                .orElseThrow(() -> new UnauthorizedException("Geçersiz refresh token"));

        validateSessionActive(session);

        if (!passwordEncoder.matches(parts.rawToken(), session.getRefreshTokenHash())) {
            throw new UnauthorizedException("Geçersiz refresh token");
        }

        User user = userRepository.findById(session.getUserId())
                .orElseThrow(() -> new UnauthorizedException("Kullanıcı bulunamadı"));

        String newRawRefreshToken = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();

        session.setRefreshTokenHash(passwordEncoder.encode(newRawRefreshToken));
        session.setAccessExpiresAt(now.plus(jwtPropertiesHelper.getAccessDuration()));
        session.setLastActivityAt(now);
        sessionRepository.save(session);

        String accessToken = jwtService.generateAccessToken(user.getEmail(), session.getId());
        String newRefreshToken = formatRefreshToken(session.getId(), newRawRefreshToken);

        return buildAuthResponse(user, session, accessToken, newRefreshToken);
    }

    @Transactional
    public void revokeSession(UUID sessionId, Long userId) {
        UserSession session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new BadRequestException("Oturum bulunamadı"));

        revoke(session);
    }

    @Transactional
    public void revokeByRefreshToken(String refreshToken) {
        RefreshTokenParts parts = parseRefreshToken(refreshToken);

        UserSession session = sessionRepository.findById(parts.sessionId())
                .orElseThrow(() -> new UnauthorizedException("Geçersiz refresh token"));

        if (!passwordEncoder.matches(parts.rawToken(), session.getRefreshTokenHash())) {
            throw new UnauthorizedException("Geçersiz refresh token");
        }

        revoke(session);
    }

    @Transactional
    public void revokeByAccessSessionId(UUID sessionId) {
        UserSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BadRequestException("Oturum bulunamadı"));

        revoke(session);
    }

    @Transactional(readOnly = true)
    public boolean isSessionActive(UUID sessionId) {
        return sessionRepository.findById(sessionId)
                .map(UserSession::isActive)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> getUserSessions(Long userId) {
        return sessionRepository.findByUserIdOrderByLoggedInAtDesc(userId).stream()
                .map(this::toSessionResponse)
                .toList();
    }

    private void validateSessionActive(UserSession session) {
        if (session.isRevoked()) {
            throw new UnauthorizedException("Oturum iptal edilmiş");
        }
        if (session.isRefreshExpired()) {
            throw new UnauthorizedException("Oturum süresi dolmuş");
        }
    }

    private void revoke(UserSession session) {
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
            throw new UnauthorizedException("Geçersiz refresh token formatı");
        }

        try {
            UUID sessionId = UUID.fromString(refreshToken.substring(0, separatorIndex));
            String rawToken = refreshToken.substring(separatorIndex + 1);
            return new RefreshTokenParts(sessionId, rawToken);
        } catch (IllegalArgumentException e) {
            throw new UnauthorizedException("Geçersiz refresh token formatı");
        }
    }

    private String formatRefreshToken(UUID sessionId, String rawToken) {
        return sessionId + "." + rawToken;
    }

    public AuthResponse buildAuthResponse(User user, UserSession session, String accessToken, String refreshToken) {
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .type("Bearer")
                .sessionId(session.getId())
                .userId(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .loggedInAt(session.getLoggedInAt())
                .accessExpiresAt(session.getAccessExpiresAt())
                .refreshExpiresAt(session.getRefreshExpiresAt())
                .revoked(session.isRevoked())
                .ipAddress(session.getIpAddress())
                .userAgent(session.getUserAgent())
                .device(session.getDevice())
                .deviceType(session.getDeviceType())
                .build();
    }

    private SessionResponse toSessionResponse(UserSession session) {
        return SessionResponse.builder()
                .sessionId(session.getId())
                .loggedInAt(session.getLoggedInAt())
                .lastActivityAt(session.getLastActivityAt())
                .accessExpiresAt(session.getAccessExpiresAt())
                .refreshExpiresAt(session.getRefreshExpiresAt())
                .revoked(session.isRevoked())
                .revokedAt(session.getRevokedAt())
                .expired(session.isRefreshExpired())
                .active(session.isActive())
                .ipAddress(session.getIpAddress())
                .userAgent(session.getUserAgent())
                .device(session.getDevice())
                .deviceType(session.getDeviceType())
                .build();
    }

    public record SessionTokens(UserSession session, String accessToken, String refreshToken, User user) {
    }

    private record RefreshTokenParts(UUID sessionId, String rawToken) {
    }
}
