package com.ael.algoryqrservice.demo;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.dto.AuthResponse;
import com.ael.algoryqrservice.model.dto.LoginRequest;
import com.ael.algoryqrservice.model.dto.LogoutRequest;
import com.ael.algoryqrservice.model.dto.SessionPageResponse;
import com.ael.algoryqrservice.repository.UserRepository;
import com.ael.algoryqrservice.security.JwtAccessPrincipal;
import com.ael.algoryqrservice.service.JwtService;
import com.ael.algoryqrservice.util.ClientInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DemoAuthService {

    private final DemoCredentials credentials;
    private final DemoSessionStore sessions;
    private final UserRepository userRepository;
    private final JwtService jwtService;

    public Optional<AuthResponse> loginIfDemo(LoginRequest request, ClientInfo clientInfo) {
        if (!credentials.accept(request)) {
            return Optional.empty();
        }
        User user = userRepository.findByEmail(credentials.email())
                .orElseThrow(() -> new BadRequestException("Demo hesabı bulunamadı"));
        return Optional.of(sessions.open(user, clientInfo));
    }

    public Optional<AuthResponse> refreshIfDemo(String refreshToken) {
        return sessions.refresh(refreshToken);
    }

    public boolean logoutIfDemo(String accessToken, LogoutRequest request) {
        if (revokeAccess(accessToken)) {
            return true;
        }
        if (request == null) {
            return false;
        }
        return sessions.revokeRefresh(request.getRefreshToken());
    }

    public boolean isCurrentDemo() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.getDetails() instanceof JwtAccessPrincipal principal
                && principal.isDemo();
    }

    public SessionPageResponse listCurrentSessions(UUID currentSessionId) {
        return sessions.page(currentSessionId);
    }

    public void revokeSession(UUID sessionId) {
        sessions.revoke(sessionId);
    }

    private boolean revokeAccess(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return false;
        }
        return jwtService.extractSessionIdIfSignatureValid(accessToken)
                .map(sessions::revokeAccess)
                .orElse(false);
    }
}
