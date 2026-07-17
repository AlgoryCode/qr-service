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

    private static final String PROVIDER_CONFLICT_MESSAGE =
            "Bu hesap farklı bir giriş yöntemiyle oluşturulmuş";

    private final UserRepository userRepository;
    private final PackageActivationService packageActivationService;

    @Transactional
    public User resolve(GoogleAuthIntent intent, GoogleOidcIdentity identity, ClientInfo clientInfo) {
        requireVerifiedEmail(identity);
        return switch (intent) {
            case LOGIN -> login(identity);
            case REGISTER -> register(identity, clientInfo);
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
            throw new BadRequestException(PROVIDER_CONFLICT_MESSAGE);
        }

        throw new UnauthorizedException("Bu e-posta adresi ile Google hesabı kayıtlı değil");
    }

    private User register(GoogleOidcIdentity identity, ClientInfo clientInfo) {
        Optional<User> existingGoogleUser = userRepository.findByProviderAndProviderSubject(
                AuthProvider.GOOGLE,
                identity.subject()
        );
        if (existingGoogleUser.isPresent()) {
            return existingGoogleUser.get();
        }

        Optional<User> existingByEmail = userRepository.findByEmail(identity.email());
        if (existingByEmail.isPresent()) {
            if (existingByEmail.get().getProvider() != AuthProvider.GOOGLE) {
                throw new BadRequestException(PROVIDER_CONFLICT_MESSAGE);
            }
            throw new BadRequestException("Bu e-posta adresi zaten kayıtlı");
        }

        User user = userRepository.saveAndFlush(User.builder()
                .firstName(identity.firstName())
                .lastName(identity.lastName())
                .email(identity.email())
                .password(null)
                .role(UserRole.USER)
                .provider(AuthProvider.GOOGLE)
                .providerSubject(identity.subject())
                .registrationIpAddress(clientInfo.ipAddress())
                .registrationUserAgent(clientInfo.userAgent())
                .registrationDevice(clientInfo.device())
                .registrationDeviceType(clientInfo.deviceType())
                .build());
        packageActivationService.ensureFreePackage(user.getId());
        return user;
    }

    private void requireVerifiedEmail(GoogleOidcIdentity identity) {
        if (!identity.emailVerified()) {
            throw new UnauthorizedException("Doğrulanmış Google e-posta adresi zorunludur");
        }
    }
}
