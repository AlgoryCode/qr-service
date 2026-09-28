package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.UnauthorizedException;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.dto.GoogleOidcIdentity;
import com.ael.algoryqrservice.model.enums.AuthProvider;
import com.ael.algoryqrservice.model.enums.GoogleAuthIntent;
import com.ael.algoryqrservice.model.enums.UserRole;
import com.ael.algoryqrservice.repository.UserRepository;
import com.ael.algoryqrservice.util.ClientInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GoogleOAuthUserService {

    static final String BASIC_ACCOUNT_GOOGLE_LOGIN_MESSAGE =
            "Bu e-posta adresi e-posta/şifre ile kayıtlı. Lütfen e-posta ve şifre ile giriş yapın.";

    private final UserRepository userRepository;
    private final PackageActivationService packageActivationService;

    @Transactional
    public User resolve(GoogleAuthIntent intent, GoogleOidcIdentity identity, ClientInfo clientInfo) {
        return resolve(intent, identity, clientInfo, AuthProvider.GOOGLE);
    }

    @Transactional
    public User resolve(
            GoogleAuthIntent intent,
            GoogleOidcIdentity identity,
            ClientInfo clientInfo,
            AuthProvider authProvider
    ) {
        requireVerifiedEmail(identity);
        AuthProvider googleProvider = requireGoogleProvider(authProvider);
        return switch (intent) {
            case LOGIN -> login(identity);
            case REGISTER -> register(identity, clientInfo, googleProvider);
            case CUSTOMER_LOGIN, CUSTOMER_REGISTER ->
                    throw new BadRequestException("Geçersiz uygulama Google kimlik doğrulama amacı");
        };
    }

    private User login(GoogleOidcIdentity identity) {
        Optional<User> googleUser = findGoogleUser(identity.subject());
        if (googleUser.isPresent()) {
            return googleUser.get();
        }

        Optional<User> existingByEmail = liveUser(userRepository.findByEmail(identity.email()));
        if (existingByEmail.isPresent() && !isGoogleProvider(existingByEmail.get().getProvider())) {
            throw new BadRequestException(providerConflictMessage(existingByEmail.get().getProvider()));
        }

        throw new UnauthorizedException("Bu e-posta adresi ile Google hesabı kayıtlı değil");
    }

    private User register(GoogleOidcIdentity identity, ClientInfo clientInfo, AuthProvider authProvider) {
        if (findGoogleUser(identity.subject()).isPresent()) {
            throw new BadRequestException("Bu e-posta adresi zaten kayıtlı");
        }

        Optional<User> existingByEmail = liveUser(userRepository.findByEmail(identity.email()));
        if (existingByEmail.isPresent()) {
            if (!isGoogleProvider(existingByEmail.get().getProvider())) {
                throw new BadRequestException(providerConflictMessage(existingByEmail.get().getProvider()));
            }
            throw new BadRequestException("Bu e-posta adresi zaten kayıtlı");
        }

        User user = userRepository.saveAndFlush(newGoogleUser(identity, clientInfo, authProvider));
        packageActivationService.ensureSubscriptionState(user.getId());
        return user;
    }

    private static User newGoogleUser(
            GoogleOidcIdentity identity,
            ClientInfo clientInfo,
            AuthProvider authProvider
    ) {
        return User.builder()
                .firstName(identity.firstName())
                .lastName(identity.lastName())
                .email(identity.email())
                .password(null)
                .emailVerified(true)
                .role(UserRole.USER)
                .provider(authProvider)
                .providerSubject(identity.subject())
                .registrationIpAddress(clientInfo.ipAddress())
                .registrationUserAgent(clientInfo.userAgent())
                .registrationDevice(clientInfo.device())
                .registrationDeviceType(clientInfo.deviceType())
                .build();
    }

    private Optional<User> findGoogleUser(String subject) {
        Optional<User> googleUser = liveUser(userRepository.findByProviderAndProviderSubject(
                AuthProvider.GOOGLE,
                subject
        ));
        if (googleUser.isPresent()) {
            return googleUser;
        }
        return liveUser(userRepository.findByProviderAndProviderSubject(AuthProvider.MOBILE_GOOGLE, subject));
    }

    private static Optional<User> liveUser(Optional<User> user) {
        return user.filter(candidate -> candidate.getDeletedAt() == null);
    }

    private static AuthProvider requireGoogleProvider(AuthProvider authProvider) {
        if (authProvider == null || !authProvider.isGoogle()) {
            throw new BadRequestException("Geçersiz Google kimlik doğrulama yöntemi");
        }
        return authProvider;
    }

    private static boolean isGoogleProvider(AuthProvider provider) {
        return provider != null && provider.isGoogle();
    }

    private void requireVerifiedEmail(GoogleOidcIdentity identity) {
        if (!identity.emailVerified()) {
            throw new UnauthorizedException("Doğrulanmış Google e-posta adresi zorunludur");
        }
    }

    static String providerConflictMessage(AuthProvider provider) {
        if (provider == AuthProvider.BASIC) {
            return BASIC_ACCOUNT_GOOGLE_LOGIN_MESSAGE;
        }
        return "Bu hesap farklı bir giriş yöntemiyle oluşturulmuş";
    }
}
