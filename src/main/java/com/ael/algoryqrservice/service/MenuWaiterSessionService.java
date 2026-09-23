package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.NotFoundException;
import com.ael.algoryqrservice.exception.UnauthorizedException;
import com.ael.algoryqrservice.model.MerchantStaff;
import com.ael.algoryqrservice.model.MerchantStaffSession;
import com.ael.algoryqrservice.model.dto.MenuWaiterDtos;
import com.ael.algoryqrservice.repository.MerchantStaffRepository;
import com.ael.algoryqrservice.repository.MerchantStaffSessionRepository;
import com.ael.algoryqrservice.util.ClientInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MenuWaiterSessionService {

    private final MerchantStaffSessionRepository sessionRepository;
    private final MerchantStaffRepository merchantStaffRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final JwtPropertiesHelper jwtPropertiesHelper;

    @Transactional
    public SessionTokens createSession(MerchantStaff waiter, ClientInfo clientInfo) {
        UUID sessionId = UUID.randomUUID();
        String rawRefreshToken = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();

        MerchantStaffSession session = MerchantStaffSession.builder()
                .id(sessionId)
                .staffId(waiter.getId())
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

        String accessToken = jwtService.generateWaiterAccessToken(
                waiter.getUsername(),
                sessionId,
                waiter.getId(),
                waiter.getBranchId(),
                waiter.getMerchantId(),
                waiter.resolvedStaffRole().name()
        );
        String refreshToken = formatRefreshToken(sessionId, rawRefreshToken);

        return new SessionTokens(session, accessToken, refreshToken, waiter);
    }

    @Transactional
    public MenuWaiterDtos.WaiterAuthResponse refresh(String refreshToken) {
        RefreshTokenParts parts = parseRefreshToken(refreshToken);

        MerchantStaffSession session = sessionRepository.findById(parts.sessionId())
                .orElseThrow(() -> new UnauthorizedException("Geçersiz refresh token"));

        validateSessionActive(session);

        if (!passwordEncoder.matches(parts.rawToken(), session.getRefreshTokenHash())) {
            throw new UnauthorizedException("Geçersiz refresh token");
        }

        MerchantStaff waiter = merchantStaffRepository.findById(session.getStaffId())
                .orElseThrow(() -> new UnauthorizedException("Garson bulunamadı"));

        if (!waiter.isActive()) {
            throw new UnauthorizedException("Garson hesabı pasif");
        }

        String newRawRefreshToken = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();

        session.setRefreshTokenHash(passwordEncoder.encode(newRawRefreshToken));
        session.setAccessExpiresAt(now.plus(jwtPropertiesHelper.getAccessDuration()));
        session.setLastActivityAt(now);
        sessionRepository.save(session);

        String accessToken = jwtService.generateWaiterAccessToken(
                waiter.getUsername(),
                session.getId(),
                waiter.getId(),
                waiter.getBranchId(),
                waiter.getMerchantId(),
                waiter.resolvedStaffRole().name()
        );
        String newRefreshToken = formatRefreshToken(session.getId(), newRawRefreshToken);

        return buildAuthResponse(accessToken, newRefreshToken, waiter);
    }

    @Transactional
    public void revokeSession(UUID sessionId, Long staffId) {
        MerchantStaffSession session = sessionRepository.findByIdAndStaffId(sessionId, staffId)
                .orElseThrow(() -> new NotFoundException("Oturum bulunamadı"));
        revoke(session);
    }

    @Transactional
    public void revokeByRefreshToken(String refreshToken) {
        RefreshTokenParts parts = parseRefreshToken(refreshToken);

        MerchantStaffSession session = sessionRepository.findById(parts.sessionId())
                .orElseThrow(() -> new UnauthorizedException("Geçersiz refresh token"));

        if (!passwordEncoder.matches(parts.rawToken(), session.getRefreshTokenHash())) {
            throw new UnauthorizedException("Geçersiz refresh token");
        }

        revoke(session);
    }

    @Transactional
    public void revokeByAccessSessionId(UUID sessionId) {
        MerchantStaffSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BadRequestException("Oturum bulunamadı"));
        revoke(session);
    }

    @Transactional(readOnly = true)
    public boolean isSessionActive(UUID sessionId) {
        return sessionRepository.findById(sessionId)
                .map(MerchantStaffSession::isActive)
                .orElse(false);
    }

    public MenuWaiterDtos.WaiterAuthResponse buildAuthResponse(
            String accessToken,
            String refreshToken,
            MerchantStaff waiter
    ) {
        return MenuWaiterDtos.WaiterAuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .staffId(waiter.getId())
                .branchId(waiter.getBranchId())
                .displayName(waiter.getDisplayName())
                .staffRole(waiter.resolvedStaffRole())
                .build();
    }

    private void validateSessionActive(MerchantStaffSession session) {
        if (session.isRevoked()) {
            throw new UnauthorizedException("Oturum iptal edilmiş");
        }
        if (session.isRefreshExpired()) {
            throw new UnauthorizedException("Oturum süresi dolmuş");
        }
    }

    private void revoke(MerchantStaffSession session) {
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

    public record SessionTokens(
            MerchantStaffSession session,
            String accessToken,
            String refreshToken,
            MerchantStaff waiter
    ) {
    }

    private record RefreshTokenParts(UUID sessionId, String rawToken) {
    }
}
