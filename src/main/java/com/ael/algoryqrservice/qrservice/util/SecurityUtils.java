package com.ael.algoryqrservice.qrservice.util;

import com.ael.algoryqrservice.authservice.application.auth.AuthSubject;
import com.ael.algoryqrservice.authservice.security.AuthPrincipal;
import com.ael.algoryqrservice.qrservice.client.AuthMerchantClient;
import com.ael.algoryqrservice.qrservice.exception.UnauthorizedException;
import com.ael.algoryqrservice.customerservice.infrastructure.persistence.entity.Customer;
import com.ael.algoryqrservice.staffservice.infrastructure.persistence.entity.MerchantStaff;
import com.ael.algoryqrservice.qrservice.model.User;
import com.ael.algoryqrservice.customerservice.infrastructure.persistence.repository.CustomerRepository;
import com.ael.algoryqrservice.staffservice.infrastructure.persistence.repository.MerchantStaffRepository;
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
                    return authPrincipal.merchantId();
                }
            }
            if (authentication.getDetails() instanceof JwtAccessPrincipal principal
                    && !principal.isCustomer()) {
                if (principal.merchantId() != null) {
                    return principal.merchantId();
                }
                if (principal.staffId() != null) {
                    Optional<Long> fromStaff = merchantStaffRepository.findById(principal.staffId())
                            .map(MerchantStaff::getMerchantId);
                    if (fromStaff.isPresent()) {
                        return fromStaff.get();
                    }
                    if (principal.merchantId() != null) {
                        return principal.merchantId();
                    }
                    throw new UnauthorizedException("Personel bulunamadı");
                }
                if (!principal.isWaiter() && principal.merchantId() != null) {
                    return resolveMerchantAccountId(principal.merchantId(), authentication.getName());
                }
            }
        }
        User user = getCurrentUser();
        return resolveMerchantAccountId(user.getId(), user.getEmail());
    }

    public boolean matchesTokenMerchant(Long merchantId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (merchantId == null || authentication == null || !(authentication.getDetails() instanceof JwtAccessPrincipal principal)) {
            return false;
        }
        if (merchantId.equals(principal.merchantId())) {
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
                && principal.customerId() != null
                && (principal.isCustomer() || hasRole(authentication, "ROLE_CUSTOMER"))) {
            return Optional.of(principal.customerId());
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

    public Long getCurrentStaffId() {
        return findCurrentStaffId()
                .orElseThrow(() -> new UnauthorizedException("Personel oturumu gerekli"));
    }

    public Long getCurrentWaiterId() {
        return getCurrentStaffId();
    }

    public Optional<Long> findCurrentStaffId() {
        return findCurrentWaiterId();
    }

    public Optional<Long> findCurrentWaiterId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        if (authentication.getDetails() instanceof JwtAccessPrincipal principal
                && principal.staffId() != null
                && isStaffAuthentication(authentication, principal)) {
            return Optional.of(principal.staffId());
        }
        return Optional.empty();
    }

    public Long getCurrentWaiterBranchId() {
        Authentication authentication = requireAuthentication("Personel oturumu gerekli");
        if (authentication.getDetails() instanceof JwtAccessPrincipal principal
                && isStaffAuthentication(authentication, principal)) {
            if (principal.branchId() != null) {
                return principal.branchId();
            }
            if (principal.staffId() != null) {
                return merchantStaffRepository.findById(principal.staffId())
                        .map(MerchantStaff::getBranchId)
                        .filter(id -> id != null)
                        .orElseThrow(() -> new UnauthorizedException("Şube bilgisi bulunamadı"));
            }
        }
        throw new UnauthorizedException("Şube bilgisi bulunamadı");
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

    /** Staff panel JWT: staffId claim yeterli; garson/mutfak/kurye rolü API'de ayrılmaz. */
    private boolean isStaffAuthentication(Authentication authentication, JwtAccessPrincipal principal) {
        if (principal.staffId() != null) {
            return true;
        }
        if (principal.isWaiter()) {
            return true;
        }
        return hasRole(authentication, "ROLE_WAITER")
                || hasRole(authentication, "ROLE_KITCHEN")
                || hasRole(authentication, "ROLE_COURIER")
                || hasRole(authentication, "ROLE_STAFF");
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role::equals);
    }
}
