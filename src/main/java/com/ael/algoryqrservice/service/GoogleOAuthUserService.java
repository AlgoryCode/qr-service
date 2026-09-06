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
        requireVerifiedEmail(identity);
        return switch (intent) {
            case LOGIN -> login(identity);
            case REGISTER -> register(identity, clientInfo);
            case CUSTOMER_LOGIN, CUSTOMER_REGISTER ->
                    throw new BadRequestException("Geçersiz uygulama Google kimlik doğrulama amacı");
        };
    }

    private User login(GoogleOidcIdentity identity) {
        Optional<User> googleUser = userRepository.findByProviderAndProviderSubject(
                AuthProvider.GOOGLE,
                identity.subject()
        );
        if (googleUser.isPresent()) {
            return googleUser.get();
        }

        Optional<User> existingByEmail = userRepository.findByEmail(identity.email());
        if (existingByEmail.isPresent() && existingByEmail.get().getProvider() != AuthProvider.GOOGLE) {
            throw new BadRequestException(providerConflictMessage(existingByEmail.get().getProvider()));
        }

        throw new UnauthorizedException("Bu e-posta adresi ile Google hesabı kayıtlı değil");
    }

    private User register(GoogleOidcIdentity identity, ClientInfo clientInfo) {
        Optional<User> existingGoogleUser = userRepository.findByProviderAndProviderSubject(
                AuthProvider.GOOGLE,
                identity.subject()
        );
        if (existingGoogleUser.isPresent()) {
            throw new BadRequestException("Bu e-posta adresi zaten kayıtlı");
        }

        Optional<User> existingByEmail = userRepository.findByEmail(identity.email());
        if (existingByEmail.isPresent()) {
            if (existingByEmail.get().getProvider() != AuthProvider.GOOGLE) {
                throw new BadRequestException(providerConflictMessage(existingByEmail.get().getProvider()));
            }
            throw new BadRequestException("Bu e-posta adresi zaten kayıtlı");
        }

        User user = userRepository.saveAndFlush(User.builder()
                .firstName(identity.firstName())
                .lastName(identity.lastName())
                .email(identity.email())
                .password(null)
                .emailVerified(true)
                .role(UserRole.USER)
                .provider(AuthProvider.GOOGLE)
                .providerSubject(identity.subject())
                .registrationIpAddress(clientInfo.ipAddress())
                .registrationUserAgent(clientInfo.userAgent())
                .registrationDevice(clientInfo.device())
                .registrationDeviceType(clientInfo.deviceType())
                .build());
        packageActivationService.ensureSubscriptionState(user.getId());
        return user;
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
