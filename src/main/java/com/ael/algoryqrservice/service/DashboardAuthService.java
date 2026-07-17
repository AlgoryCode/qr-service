package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.ForbiddenException;
import com.ael.algoryqrservice.model.DashboardUser;
import com.ael.algoryqrservice.model.dto.AuthResponse;
import com.ael.algoryqrservice.model.dto.LoginRequest;
import com.ael.algoryqrservice.model.dto.LogoutRequest;
import com.ael.algoryqrservice.model.dto.RefreshTokenRequest;
import com.ael.algoryqrservice.repository.DashboardUserRepository;
import com.ael.algoryqrservice.util.ClientInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DashboardAuthService {

    private final DashboardUserRepository dashboardUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final DashboardSessionService dashboardSessionService;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse login(LoginRequest request, ClientInfo clientInfo) {
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
        throw new BadRequestException("Cikis icin gecerli access token veya refresh token gerekli");
    }

    private DashboardUser authenticate(LoginRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        DashboardUser user = dashboardUserRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new BadCredentialsException("Gecersiz kimlik bilgileri"));
        if (!user.isActive()) {
            throw new ForbiddenException("Hesap pasif");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("Gecersiz kimlik bilgileri");
        }
        return user;
    }
}