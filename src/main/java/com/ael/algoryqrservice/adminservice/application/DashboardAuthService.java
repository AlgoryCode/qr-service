package com.ael.algoryqrservice.adminservice.application;

import com.ael.algoryqrservice.adminservice.dto.DashboardAuthDtos.MfaChallengeResponse;
import com.ael.algoryqrservice.adminservice.infrastructure.persistence.entity.DashboardUser;
import com.ael.algoryqrservice.adminservice.infrastructure.persistence.repository.DashboardUserRepository;
import com.ael.algoryqrservice.adminservice.security.MfaChallenges;
import com.ael.algoryqrservice.adminservice.security.Totp;
import com.ael.algoryqrservice.qrservice.account.service.JwtService;
import com.ael.algoryqrservice.qrservice.exception.BadRequestException;
import com.ael.algoryqrservice.qrservice.exception.ForbiddenException;
import com.ael.algoryqrservice.qrservice.exception.UnauthorizedException;
import com.ael.algoryqrservice.qrservice.model.dto.AuthResponse;
import com.ael.algoryqrservice.qrservice.model.dto.LoginRequest;
import com.ael.algoryqrservice.qrservice.model.dto.LogoutRequest;
import com.ael.algoryqrservice.qrservice.model.dto.RefreshTokenRequest;
import com.ael.algoryqrservice.qrservice.util.AppTime;
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
    private final MfaChallenges mfaChallenges;

    /** Tokens, or an {@link MfaChallengeResponse} when the account asks for a second factor. */
    @Transactional
    public Object login(LoginRequest request, ClientInfo clientInfo) {
        log.info("Dashboard login identifier={} clientIp={}", request.identifier(), clientInfo.ipAddress());
        DashboardUser user = authenticate(request);
        if (user.isTotpEnabled()) {
            return new MfaChallengeResponse(true, mfaChallenges.issue(user.getId()),
                    java.time.Duration.ofMinutes(5).toSeconds());
        }
        return signIn(user, clientInfo);
    }

    /** Second step of a two-factor login. */
    @Transactional
    public AuthResponse completeMfaLogin(String mfaToken, String code, ClientInfo clientInfo) {
        Long userId = mfaChallenges.verify(mfaToken);
        mfaChallenges.checkNotLocked(userId);
        DashboardUser user = dashboardUserRepository.findById(userId)
                .filter(DashboardUser::isActive)
                .orElseThrow(() -> new UnauthorizedException("Geçersiz kimlik bilgileri"));
        if (!user.isTotpEnabled() || !Totp.verify(user.getTotpSecret(), code, AppTime.clock().instant().getEpochSecond())) {
            mfaChallenges.recordFailure(userId);
            throw new UnauthorizedException("Doğrulama kodu hatalı");
        }
        mfaChallenges.clearFailures(userId);
        return signIn(user, clientInfo);
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

    private AuthResponse signIn(DashboardUser user, ClientInfo clientInfo) {
        user.setLastLoginAt(AppTime.nowLocal());
        dashboardUserRepository.save(user);
        return dashboardSessionService.createSession(user, clientInfo);
    }

    private DashboardUser authenticate(LoginRequest request) {
        String identifier = request.identifier().toLowerCase();
        if (identifier.isEmpty()) {
            throw new BadRequestException("Kullanıcı adı zorunludur");
        }
        DashboardUser user = (identifier.contains("@")
                ? dashboardUserRepository.findByEmailIgnoreCase(identifier)
                : dashboardUserRepository.findByUsernameIgnoreCase(identifier))
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
