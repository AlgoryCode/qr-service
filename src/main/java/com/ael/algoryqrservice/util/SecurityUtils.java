package com.ael.algoryqrservice.util;

import com.ael.algoryqrservice.exception.UnauthorizedException;
import com.ael.algoryqrservice.model.Customer;
import com.ael.algoryqrservice.model.MenuWaiter;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.repository.CustomerRepository;
import com.ael.algoryqrservice.repository.MenuWaiterRepository;
import com.ael.algoryqrservice.repository.UserRepository;
import com.ael.algoryqrservice.security.JwtAccessPrincipal;
import com.ael.algoryqrservice.service.JwtService;
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
    private final MenuWaiterRepository menuWaiterRepository;

    public User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Kullanıcı bulunamadı"));
    }

    public Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getDetails() instanceof JwtAccessPrincipal principal
                && principal.userId() != null
                && !principal.isCustomer()
                && !principal.isWaiter()) {
            return principal.userId();
        }
        return getCurrentUser().getId();
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
                return menuWaiterRepository.findById(principal.userId())
                        .map(MenuWaiter::getBranchId)
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
