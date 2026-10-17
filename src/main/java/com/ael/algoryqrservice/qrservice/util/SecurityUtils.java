package com.ael.algoryqrservice.qrservice.util;

import com.ael.algoryqrservice.authservice.application.auth.AuthSubject;
import com.ael.algoryqrservice.authservice.security.AuthPrincipal;
import com.ael.algoryqrservice.qrservice.client.AuthMerchantClient;
import com.ael.algoryqrservice.qrservice.exception.UnauthorizedException;
import com.ael.algoryqrservice.qrservice.model.Customer;
import com.ael.algoryqrservice.qrservice.model.MerchantStaff;
import com.ael.algoryqrservice.qrservice.model.User;
import com.ael.algoryqrservice.qrservice.repository.CustomerRepository;
import com.ael.algoryqrservice.qrservice.repository.MerchantStaffRepository;
import com.ael.algoryqrservice.qrservice.repository.UserRepository;
import com.ael.algoryqrservice.qrservice.security.JwtAccessPrincipal;
import com.ael.algoryqrservice.qrservice.account.service.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class SecurityUtils {

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final MerchantStaffRepository merchantStaffRepository;
    private final AuthMerchantClient authMerchantClient;

    public Optional<User> findCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByEmail(authentication.getName());
    }

    public String currentLogin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new UnauthorizedException("Kullanıcı bulunamadı");
        }
        return authentication.getName();
    }

    public User getCurrentUser() {
        return findCurrentUser().orElseThrow(() -> new UnauthorizedException("Kullanıcı bulunamadı"));
    }

    public Long getCurrentMerchantId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            if (authentication.getPrincipal() instanceof AuthPrincipal authPrincipal
                    && !AuthSubject.TYPE_CUSTOMER.equals(authPrincipal.subjectType())) {
                if (authPrincipal.merchantId() != null) {
                    return authPrincipal.merchantId();
                }
                if (AuthSubject.TYPE_MERCHANT.equals(authPrincipal.subjectType())) {
                    return authPrincipal.userId();
                }
            }
            if (authentication.getDetails() instanceof JwtAccessPrincipal principal
                    && !principal.isCustomer()) {
                if (principal.merchantId() != null) {
                    return principal.merchantId();
                }
                if (principal.isWaiter() && principal.userId() != null) {
                    return merchantStaffRepository.findById(principal.userId())
                            .map(MerchantStaff::getMerchantId)
                            .orElseThrow(() -> new UnauthorizedException("Garson bulunamadı"));
                }
                if (!principal.isWaiter() && principal.userId() != null) {
                    return resolveMerchantAccountId(principal.userId(), authentication.getName());
                }
            }
        }
        User user = getCurrentUser();
        return resolveMerchantAccountId(user.getId(), user.getEmail());
    }

    public Long getCurrentUserId() {
        return getCurrentMerchantId();
    }

    public boolean matchesTokenUser(Long merchantId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (merchantId == null || authentication == null || !(authentication.getDetails() instanceof JwtAccessPrincipal principal)) {
            return false;
        }
        if (merchantId.equals(principal.merchantId()) || merchantId.equals(principal.userId())) {
            return true;
        }
        String login = authentication.getName();
        if (login != null && !login.isBlank()) {
            Optional<Long> authMerchantId = authMerchantClient.findActiveMerchantIdByEmail(login);
            if (authMerchantId.filter(merchantId::equals).isPresent()) {
                return true;
            }
        }
        return findCurrentUser()
                .map(User::getId)
                .filter(merchantId::equals)
                .isPresent();
    }

    /**
     * Fulfillment and auth merchants are keyed by {@code tbl_merchant.id}; legacy panel rows may still use {@code tbl_user.id}.
     */
    private Long resolveMerchantAccountId(Long fallbackId, String loginEmail) {
        if (loginEmail != null && !loginEmail.isBlank()) {
            Optional<Long> authMerchantId = authMerchantClient.findActiveMerchantIdByEmail(loginEmail);
            if (authMerchantId.isPresent()) {
                return authMerchantId.get();
            }
        }
        return fallbackId;
    }

    public Customer getCurrentCustomer() {
        Authentication authentication = requireAuthentication("Müşteri bulunamadı");
        if (!isCustomerAuthentication(authentication)) {
            throw new UnauthorizedException("Müşteri bulunamadı");
        }
        String email = authentication.getName();
        return customerRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Müşteri bulunamadı"));
    }

    public Long getCurrentCustomerId() {
        return findCurrentCustomerId()
                .orElseThrow(() -> new UnauthorizedException("Müşteri bulunamadı"));
    }

    public Optional<Long> findCurrentCustomerId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        if (authentication.getDetails() instanceof JwtAccessPrincipal principal
                && principal.userId() != null
                && (principal.isCustomer() || hasRole(authentication, "ROLE_CUSTOMER"))) {
            return Optional.of(principal.userId());
        }
        if (!isCustomerAuthentication(authentication)) {
            return Optional.empty();
        }
        String email = authentication.getName();
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return customerRepository.findByEmail(email).map(Customer::getId);
    }

    public Long getCurrentWaiterId() {
        return findCurrentWaiterId()
                .orElseThrow(() -> new UnauthorizedException("Garson bulunamadı"));
    }

    public Optional<Long> findCurrentWaiterId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        if (authentication.getDetails() instanceof JwtAccessPrincipal principal
                && principal.userId() != null
                && (principal.isWaiter() || hasRole(authentication, "ROLE_WAITER"))) {
            return Optional.of(principal.userId());
        }
        return Optional.empty();
    }

    public Long getCurrentWaiterBranchId() {
        Authentication authentication = requireAuthentication("Garson bulunamadı");
        if (authentication.getDetails() instanceof JwtAccessPrincipal principal && principal.isWaiter()) {
            if (principal.branchId() != null) {
                return principal.branchId();
            }
            if (principal.userId() != null) {
                return merchantStaffRepository.findById(principal.userId())
                        .map(MerchantStaff::getBranchId)
                        .filter(id -> id != null)
                        .orElseThrow(() -> new UnauthorizedException("Garson şube bilgisi bulunamadı"));
            }
        }
        throw new UnauthorizedException("Garson şube bilgisi bulunamadı");
    }

    private Authentication requireAuthentication(String message) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException(message);
        }
        return authentication;
    }

    private boolean isCustomerAuthentication(Authentication authentication) {
        if (authentication.getDetails() instanceof JwtAccessPrincipal principal
                && JwtService.PRINCIPAL_CUSTOMER.equals(principal.principalType())) {
            return true;
        }
        return hasRole(authentication, "ROLE_CUSTOMER");
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role::equals);
    }
}
