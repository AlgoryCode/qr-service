package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.UnauthorizedException;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.enums.AuthProvider;
import com.ael.algoryqrservice.model.dto.*;
import com.ael.algoryqrservice.repository.DashboardUserRepository;
import com.ael.algoryqrservice.repository.UserRepository;
import com.ael.algoryqrservice.util.ClientInfo;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final DashboardUserRepository dashboardUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final SessionService sessionService;
    private final JwtService jwtService;
    private final PackageActivationService packageActivationService;

    @Transactional
    public RegisterResponse register(RegisterRequest request, ClientInfo clientInfo) {
        if (!request.getPassword().equals(request.getPasswordConfirm())) {
            throw new BadRequestException("Şifreler eşleşmiyor");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Bu e-posta adresi zaten kayıtlı");
        }

        if (userRepository.existsByPhone(request.getPhone())) {
            throw new BadRequestException("Bu telefon numarası zaten kayıtlı");
        }

        User user = User.builder()
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName().trim())
                .email(request.getEmail().trim().toLowerCase())
                .phone(request.getPhone().trim())
                .password(passwordEncoder.encode(request.getPassword()))
                .provider(AuthProvider.BASIC)
                .registrationIpAddress(clientInfo.ipAddress())
                .registrationUserAgent(clientInfo.userAgent())
                .registrationDevice(clientInfo.device())
                .registrationDeviceType(clientInfo.deviceType())
                .build();

        User saved = userRepository.save(user);
        packageActivationService.ensureFreePackage(saved.getId());

        return RegisterResponse.builder()
                .message("Kayıt başarılı")
                .userId(saved.getId())
                .email(saved.getEmail())
                .firstName(saved.getFirstName())
                .lastName(saved.getLastName())
                .registrationIpAddress(saved.getRegistrationIpAddress())
                .registrationUserAgent(saved.getRegistrationUserAgent())
                .registrationDevice(saved.getRegistrationDevice())
                .registrationDeviceType(saved.getRegistrationDeviceType())
                .build();
    }

    @Transactional
    public AuthResponse login(LoginRequest request, ClientInfo clientInfo) {
        User user = authenticate(request);
        packageActivationService.ensureFreePackage(user.getId());
        return createAuthResponse(user, clientInfo);
    }

    private User authenticate(LoginRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        if (dashboardUserRepository.existsByEmailIgnoreCase(email)) {
            throw new BadRequestException("Bu hesap dashboard girisi icindir. /dashboard/auth/login kullanin");
        }
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Geçersiz kimlik bilgileri"));
        if (user.getProvider() != AuthProvider.BASIC) {
            throw new BadCredentialsException("Geçersiz kimlik bilgileri");
        }

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.getPassword())
        );

        return user;
    }

    private AuthResponse createAuthResponse(User user, ClientInfo clientInfo) {
        SessionService.SessionTokens tokens = sessionService.createSession(user, clientInfo);
        return sessionService.buildAuthResponse(
                tokens.accessToken(),
                tokens.refreshToken()
        );
    }

    public AuthResponse refresh(RefreshTokenRequest request) {
        return sessionService.refresh(request.getRefreshToken());
    }

    @Transactional
    public void logout(LogoutRequest request, String accessToken) {
        if (accessToken != null && !accessToken.isBlank()) {
            var revoked = jwtService.extractSessionIdIfSignatureValid(accessToken)
                    .map(sessionId -> {
                        sessionService.revokeByAccessSessionId(sessionId);
                        return true;
                    })
                    .orElse(false);
            if (revoked) {
                return;
            }
        }

        if (request != null && request.getRefreshToken() != null && !request.getRefreshToken().isBlank()) {
            sessionService.revokeByRefreshToken(request.getRefreshToken());
            return;
        }

        throw new BadRequestException("Çıkış için geçerli access token veya refresh token gerekli");
    }

    public List<SessionResponse> getMySessions() {
        User user = getCurrentUser();
        return sessionService.getUserSessions(user.getId());
    }

    @Transactional
    public void revokeSession(UUID sessionId) {
        User user = getCurrentUser();
        sessionService.revokeSession(sessionId, user.getId());
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Kullanıcı bulunamadı"));
    }

}
