package com.ael.algoryqrservice.qrservice.account.service;

import com.ael.algoryqrservice.qrservice.access.AccessSession;
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

@Service
@RequiredArgsConstructor
public class SessionContextService {

    private final UserRepository userRepository;
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
        return userRepository.findById(merchantId)
                .map(this::fromUser)
                .orElseGet(() -> fromToken(merchantId));
    }

    private SessionUserResponse fromUser(User user) {
        return new SessionUserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPhone(),
                user.getProvider(),
                user.getRole()
        );
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
        if (principal.merchantId() == null && principal.userId() == null) {
            return true;
        }
        return merchantId.equals(principal.merchantId()) || merchantId.equals(principal.userId());
    }

}
