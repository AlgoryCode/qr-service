package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.demo.DemoAccess;
import com.ael.algoryqrservice.demo.DemoAuthService;
import com.ael.algoryqrservice.exception.AuthErrorCodes;
import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.ForbiddenException;
import com.ael.algoryqrservice.exception.UnauthorizedException;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.enums.AuthProvider;
import com.ael.algoryqrservice.model.enums.UserRole;
import com.ael.algoryqrservice.model.dto.*;
import com.ael.algoryqrservice.repository.DashboardUserRepository;
import com.ael.algoryqrservice.repository.MerchantStaffRepository;
import com.ael.algoryqrservice.repository.UserRepository;
import com.ael.algoryqrservice.security.LoginAttemptGuard;
import com.ael.algoryqrservice.util.ClientInfo;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String GOOGLE_ACCOUNT_BASIC_LOGIN_MESSAGE =
            "Bu e-posta adresi Google ile kayıtlı. Lütfen Google ile giriş yapın.";

    private final UserRepository userRepository;
    private final DashboardUserRepository dashboardUserRepository;
    private final MerchantStaffRepository merchantStaffRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final SessionService sessionService;
    private final JwtService jwtService;
    private final PackageActivationService packageActivationService;
    private final UserAccessProfileService userAccessProfileService;
    private final EmailVerificationService emailVerificationService;
    private final LoginAttemptGuard loginAttemptGuard;
    private final DemoAuthService demoAuthService;

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
        emailVerificationService.sendForUser(saved);
        packageActivationService.ensureSubscriptionState(saved.getId());

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

    public AuthResponse login(LoginRequest request, ClientInfo clientInfo) {
        return demoAuthService.loginIfDemo(request, clientInfo)
                .orElseGet(() -> loginPersisted(request, clientInfo));
    }

    private AuthResponse loginPersisted(LoginRequest request, ClientInfo clientInfo) {
        User user = authenticate(request);
        requireEmailVerified(user);
        packageActivationService.ensureSubscriptionState(user.getId());
        return createAuthResponse(user, clientInfo);
    }

    private void requireEmailVerified(User user) {
        if (user.getProvider() == AuthProvider.BASIC && !user.isEmailVerified()) {
            throw new ForbiddenException(
                    AuthErrorCodes.EMAIL_NOT_VERIFIED,
                    AuthErrorCodes.EMAIL_NOT_VERIFIED_MESSAGE
            );
        }
    }

    private User authenticate(LoginRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        loginAttemptGuard.assertNotBlocked(email);
        if (dashboardUserRepository.existsByEmailIgnoreCase(email)) {
            throw new BadRequestException("Bu hesap dashboard girisi icindir. /admin/auth/sessions kullanin");
        }
        if (merchantStaffRepository.existsByUsernameIgnoreCase(email)) {
            throw new BadCredentialsException("Bu hesap garson (WAITER) hesabıdır. Panel girişi yapılamaz.");
        }
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Geçersiz kimlik bilgileri"));
        if (user.getRole() == UserRole.WAITER) {
            throw new BadCredentialsException("Bu hesap garson (WAITER) hesabıdır. Panel girişi yapılamaz.");
        }
        if (user.getProvider() != null && user.getProvider().isGoogle()) {
            throw new BadRequestException(GOOGLE_ACCOUNT_BASIC_LOGIN_MESSAGE);
        }
        if (user.getProvider() != AuthProvider.BASIC) {
            throw new BadRequestException("Bu hesap farklı bir giriş yöntemiyle oluşturulmuş");
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.getPassword())
            );
        } catch (AuthenticationException ex) {
            loginAttemptGuard.registerFailure(email);
            throw ex;
        }
        loginAttemptGuard.reset(email);

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
        return demoAuthService.refreshIfDemo(request.getRefreshToken())
                .orElseGet(() -> sessionService.refresh(request.getRefreshToken()));
    }

    public void logout(LogoutRequest request, String accessToken) {
        if (demoAuthService.logoutIfDemo(accessToken, request)) {
            return;
        }
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

    public SessionPageResponse getMySessions(String accessToken, int page, int size) {
        if (demoAuthService.isCurrentDemo()) {
            UUID currentSessionId = accessToken == null || accessToken.isBlank()
                    ? null
                    : jwtService.extractSessionIdIfSignatureValid(accessToken).orElse(null);
            return demoAuthService.listCurrentSessions(currentSessionId);
        }
        User user = getCurrentUser();
        UUID currentSessionId = accessToken == null || accessToken.isBlank()
                ? null
                : jwtService.extractSessionIdIfSignatureValid(accessToken).orElse(null);
        return sessionService.getUserSessions(user.getId(), currentSessionId, page, size);
    }

    @Transactional
    public UserAccessProfile getAccessProfile() {
        if (demoAuthService.isCurrentDemo()) {
            return DemoAccess.profile();
        }
        User user = getCurrentUser();
        return userAccessProfileService.resolve(user.getId());
    }

    @Transactional
    public void revokeSession(UUID sessionId) {
        if (demoAuthService.isCurrentDemo()) {
            demoAuthService.revokeSession(sessionId);
            return;
        }
        User user = getCurrentUser();
        sessionService.revokeSession(sessionId, user.getId());
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Kullanıcı bulunamadı"));
    }

}
