package com.ael.algoryqrservice.qrservice.account.service;

import com.ael.algoryqrservice.qrservice.access.AccessSession;
import com.ael.algoryqrservice.qrservice.client.AuthMerchantClient;
import com.ael.algoryqrservice.qrservice.client.FulfillmentServiceClient;
import com.ael.algoryqrservice.qrservice.client.PackageView;
import com.ael.algoryqrservice.qrservice.exception.NotFoundException;
import com.ael.algoryqrservice.qrservice.model.User;
import com.ael.algoryqrservice.qrservice.account.dto.SessionContextResponse;
import com.ael.algoryqrservice.qrservice.account.dto.SessionUserResponse;
import com.ael.algoryqrservice.qrservice.model.enums.AccessDecision;
import com.ael.algoryqrservice.qrservice.model.enums.AuthProvider;
import com.ael.algoryqrservice.qrservice.model.enums.UserRole;
import com.ael.algoryqrservice.qrservice.repository.UserRepository;
import com.ael.algoryqrservice.qrservice.security.JwtAccessPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SessionContextService {

    private final UserRepository userRepository;
    private final AuthMerchantClient authMerchantClient;
    private final FulfillmentServiceClient fulfillmentServiceClient;
    private final SessionContextAssembler assembler;

    public SessionContextResponse resolve(Long merchantId) {
        SessionUserResponse user = sessionUser(merchantId);
        PackageView view = fulfillmentServiceClient.findPackage(merchantId);
        if (!"ACTIVE".equals(view.status()) || view.body() == null) {
            return assembler.blocked(
                    user,
                    AccessSession.of(AccessDecision.REQUIRE_PURCHASE, null, null, null)
            );
        }
        return assembler.paid(user, view.body(), fulfillmentServiceClient.listEntitlements(merchantId));
    }

    private SessionUserResponse sessionUser(Long merchantId) {
        Optional<SessionUserResponse> fromMerchant = authMerchantClient.findMerchantProfile(merchantId)
                .map(profile -> fromMerchantProfile(profile, merchantId));
        if (fromMerchant.isPresent()) {
            return fromMerchant.get();
        }
        Optional<User> user = userRepository.findById(merchantId);
        if (user.isPresent()) {
            return fromUser(user.get(), merchantId);
        }
        Optional<String> login = currentLoginEmail();
        if (login.isPresent()) {
            Optional<User> byEmail = userRepository.findByEmail(login.get());
            if (byEmail.isPresent()) {
                return fromUser(byEmail.get(), merchantId);
            }
        }
        return fromToken(merchantId);
    }

    private SessionUserResponse fromMerchantProfile(AuthMerchantClient.MerchantProfileView profile, Long merchantId) {
        return new SessionUserResponse(
                merchantId,
                profile.firstName(),
                profile.lastName(),
                profile.email(),
                profile.phone(),
                authProvider(profile.provider()),
                UserRole.USER
        );
    }

    private static AuthProvider authProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            return AuthProvider.BASIC;
        }
        try {
            return AuthProvider.valueOf(provider.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return AuthProvider.BASIC;
        }
    }

    private SessionUserResponse fromUser(User user, Long merchantId) {
        return new SessionUserResponse(
                merchantId,
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPhone(),
                user.getProvider(),
                user.getRole()
        );
    }

    private Optional<String> currentLoginEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(authentication.getName().trim());
    }

    private SessionUserResponse fromToken(Long merchantId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new NotFoundException("Kullanıcı bulunamadı");
        }
        if (authentication.getDetails() instanceof JwtAccessPrincipal principal && !ownsMerchant(principal, merchantId)) {
            throw new NotFoundException("Kullanıcı bulunamadı");
        }
        return new SessionUserResponse(
                merchantId,
                null,
                null,
                authentication.getName(),
                null,
                AuthProvider.BASIC,
                UserRole.USER
        );
    }

    private boolean ownsMerchant(JwtAccessPrincipal principal, Long merchantId) {
        if (principal.merchantId() == null && principal.merchantId() == null) {
            return true;
        }
        return merchantId.equals(principal.merchantId()) || merchantId.equals(principal.merchantId());
    }

}
