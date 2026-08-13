package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.NotFoundException;
import com.ael.algoryqrservice.exception.UnauthorizedException;
import com.ael.algoryqrservice.model.Customer;
import com.ael.algoryqrservice.model.CustomerSession;
import com.ael.algoryqrservice.model.dto.AuthResponse;
import com.ael.algoryqrservice.model.dto.CustomerAuthDtos;
import com.ael.algoryqrservice.repository.CustomerRepository;
import com.ael.algoryqrservice.repository.CustomerSessionRepository;
import com.ael.algoryqrservice.util.ClientInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerSessionService {

    private final CustomerSessionRepository sessionRepository;
    private final CustomerRepository customerRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final JwtPropertiesHelper jwtPropertiesHelper;

    @Transactional
    public SessionTokens createSession(Customer customer, ClientInfo clientInfo) {
        UUID sessionId = UUID.randomUUID();
        String rawRefreshToken = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();

        CustomerSession session = CustomerSession.builder()
                .id(sessionId)
                .customerId(customer.getId())
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

        String accessToken = jwtService.generateCustomerAccessToken(
                customer.getEmail(),
                sessionId,
                customer.getId(),
                customer.getProvider()
        );
        String refreshToken = formatRefreshToken(sessionId, rawRefreshToken);

        return new SessionTokens(session, accessToken, refreshToken, customer);
    }

    @Transactional
    public CustomerAuthDtos.CustomerAuthResponse refresh(String refreshToken) {
        RefreshTokenParts parts = parseRefreshToken(refreshToken);

        CustomerSession session = sessionRepository.findById(parts.sessionId())
                .orElseThrow(() -> new UnauthorizedException("Geçersiz refresh token"));

        validateSessionActive(session);

        if (!passwordEncoder.matches(parts.rawToken(), session.getRefreshTokenHash())) {
            throw new UnauthorizedException("Geçersiz refresh token");
        }

        Customer customer = customerRepository.findById(session.getCustomerId())
                .orElseThrow(() -> new UnauthorizedException("Müşteri bulunamadı"));

        String newRawRefreshToken = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();

        session.setRefreshTokenHash(passwordEncoder.encode(newRawRefreshToken));
        session.setAccessExpiresAt(now.plus(jwtPropertiesHelper.getAccessDuration()));
        session.setLastActivityAt(now);
        sessionRepository.save(session);

        String accessToken = jwtService.generateCustomerAccessToken(
                customer.getEmail(),
                session.getId(),
                customer.getId(),
                customer.getProvider()
        );
        String newRefreshToken = formatRefreshToken(session.getId(), newRawRefreshToken);

        return buildCustomerAuthResponse(accessToken, newRefreshToken, customer.getId());
    }

    @Transactional
    public void revokeSession(UUID sessionId, Long customerId) {
        CustomerSession session = sessionRepository.findByIdAndCustomerId(sessionId, customerId)
                .orElseThrow(() -> new NotFoundException("Oturum bulunamadı"));

        revoke(session);
    }

    @Transactional
    public void revokeByRefreshToken(String refreshToken) {
        RefreshTokenParts parts = parseRefreshToken(refreshToken);

        CustomerSession session = sessionRepository.findById(parts.sessionId())
                .orElseThrow(() -> new UnauthorizedException("Geçersiz refresh token"));

        if (!passwordEncoder.matches(parts.rawToken(), session.getRefreshTokenHash())) {
            throw new UnauthorizedException("Geçersiz refresh token");
        }

        revoke(session);
    }

    @Transactional
    public void revokeByAccessSessionId(UUID sessionId) {
        CustomerSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BadRequestException("Oturum bulunamadı"));

        revoke(session);
    }

    @Transactional(readOnly = true)
    public boolean isSessionActive(UUID sessionId) {
        return sessionRepository.findById(sessionId)
                .map(CustomerSession::isActive)
                .orElse(false);
    }

    private void validateSessionActive(CustomerSession session) {
        if (session.isRevoked()) {
            throw new UnauthorizedException("Oturum iptal edilmiş");
        }
        if (session.isRefreshExpired()) {
            throw new UnauthorizedException("Oturum süresi dolmuş");
        }
    }

    private void revoke(CustomerSession session) {
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

    public CustomerAuthDtos.CustomerAuthResponse buildCustomerAuthResponse(
            String accessToken,
            String refreshToken,
            Long customerId
    ) {
        return CustomerAuthDtos.CustomerAuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .customerId(customerId)
                .build();
    }

    public AuthResponse buildAuthResponse(String accessToken, String refreshToken) {
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    public record SessionTokens(
            CustomerSession session,
            String accessToken,
            String refreshToken,
            Customer customer
    ) {
    }

    private record RefreshTokenParts(UUID sessionId, String rawToken) {
    }
}
