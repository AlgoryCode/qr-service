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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GoogleOAuthUserService {

    private final UserRepository userRepository;
    private final UserPackageService userPackageService;

    @Transactional
    public User resolve(
            GoogleAuthIntent intent,
            GoogleOidcIdentity identity,
            ClientInfo clientInfo
    ) {
        validate(identity);
        return switch (intent) {
            case LOGIN -> login(identity);
            case REGISTER -> register(identity, clientInfo);
        };
    }

    private User login(GoogleOidcIdentity identity) {
        return userRepository.findByProviderAndProviderSubject(
                        AuthProvider.GOOGLE,
                        identity.subject()
                )
                .filter(user -> user.getEmail().equalsIgnoreCase(identity.email()))
                .orElseThrow(() -> new UnauthorizedException(
                        "Google hesabı kayıtlı değil. Önce Google ile kayıt olun."
                ));
    }

    private User register(GoogleOidcIdentity identity, ClientInfo clientInfo) {
        Optional<User> existingGoogleUser = userRepository.findByProviderAndProviderSubject(
                AuthProvider.GOOGLE,
                identity.subject()
        );
        if (existingGoogleUser.isPresent()) {
            return existingGoogleUser.get();
        }
        if (userRepository.existsByEmail(identity.email())) {
            throw new BadRequestException("Bu e-posta adresi zaten kayıtlı");
        }

        User user = User.builder()
                .firstName(defaultFirstName(identity.firstName(), identity.email()))
                .lastName(defaultLastName(identity.lastName()))
                .email(identity.email().trim().toLowerCase())
                .provider(AuthProvider.GOOGLE)
                .providerSubject(identity.subject())
                .role(UserRole.USER)
                .registrationIpAddress(clientInfo.ipAddress())
                .registrationUserAgent(clientInfo.userAgent())
                .registrationDevice(clientInfo.device())
                .registrationDeviceType(clientInfo.deviceType())
                .build();
        try {
            User saved = userRepository.saveAndFlush(user);
            userPackageService.ensureFreePackage(saved.getId());
            return saved;
        } catch (DataIntegrityViolationException exception) {
            return userRepository.findByProviderAndProviderSubject(AuthProvider.GOOGLE, identity.subject())
                    .orElseThrow(() -> new BadRequestException("Bu e-posta adresi zaten kayıtlı"));
        }
    }

    private void validate(GoogleOidcIdentity identity) {
        if (identity.subject() == null || identity.subject().isBlank()) {
            throw new UnauthorizedException("Google kullanıcı bilgisi doğrulanamadı");
        }
        if (identity.email() == null || identity.email().isBlank() || !identity.emailVerified()) {
            throw new UnauthorizedException("Doğrulanmış Google e-posta adresi gerekli");
        }
    }

    private String defaultFirstName(String firstName, String email) {
        if (firstName != null && !firstName.isBlank()) {
            return firstName.trim();
        }
        return email.substring(0, email.indexOf('@'));
    }

    private String defaultLastName(String lastName) {
        return lastName == null ? "" : lastName.trim();
    }
}
