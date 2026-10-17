package com.ael.algoryqrservice.qrservice.service;

import com.ael.algoryqrservice.qrservice.account.service.JwtService;
import com.ael.algoryqrservice.qrservice.exception.BadRequestException;
import com.ael.algoryqrservice.qrservice.exception.ForbiddenException;
import com.ael.algoryqrservice.qrservice.model.DashboardUser;
import com.ael.algoryqrservice.qrservice.model.dto.AuthResponse;
import com.ael.algoryqrservice.qrservice.model.dto.LoginRequest;
import com.ael.algoryqrservice.qrservice.model.dto.LogoutRequest;
import com.ael.algoryqrservice.qrservice.model.dto.RefreshTokenRequest;
import com.ael.algoryqrservice.qrservice.repository.DashboardUserRepository;
import com.ael.algoryqrservice.qrservice.util.ClientInfo;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DashboardAuthService {

    private static final Logger log = LoggerFactory.getLogger(DashboardAuthService.class);

    private final DashboardUserRepository dashboardUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final DashboardSessionService dashboardSessionService;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse login(LoginRequest request, ClientInfo clientInfo) {
        log.info("Dashboard login email={} clientIp={}", request.getEmail(), clientInfo.ipAddress());
        DashboardUser user = authenticate(request);
        return dashboardSessionService.createSession(user, clientInfo);
    }

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        return dashboardSessionService.refresh(request.getRefreshToken());
    }

    @Transactional
    public void logout(LogoutRequest request, String accessToken) {
        if (accessToken != null && !accessToken.isBlank()) {
            var revoked = jwtService.extractSessionIdIfSignatureValid(accessToken)
                    .map(sessionId -> {
                        dashboardSessionService.revokeByAccessSessionId(sessionId);
                        return true;
                    })
                    .orElse(false);
            if (revoked) {
                return;
            }
        }
        if (request != null && request.getRefreshToken() != null && !request.getRefreshToken().isBlank()) {
            dashboardSessionService.revokeByRefreshToken(request.getRefreshToken());
            return;
        }
        throw new BadRequestException("Çıkış için geçerli access token veya refresh token gerekli");
    }

    private DashboardUser authenticate(LoginRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        DashboardUser user = dashboardUserRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new BadCredentialsException("Geçersiz kimlik bilgileri"));
        if (!user.isActive()) {
            throw new ForbiddenException("Hesap pasif");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("Geçersiz kimlik bilgileri");
        }
        return user;
    }
}
