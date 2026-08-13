package com.ael.algoryqrservice.security;

import com.ael.algoryqrservice.service.JwtService;

import java.util.List;

public record JwtAccessPrincipal(
        Long userId,
        List<String> scopes,
        List<String> products,
        String activePackage,
        String principalType,
        Long menuId
) {
    public JwtAccessPrincipal(
            Long userId,
            List<String> scopes,
            List<String> products,
            String activePackage
    ) {
        this(userId, scopes, products, activePackage, JwtService.PRINCIPAL_APP, null);
    }

    public JwtAccessPrincipal(
            Long userId,
            List<String> scopes,
            List<String> products,
            String activePackage,
            String principalType
    ) {
        this(userId, scopes, products, activePackage, principalType, null);
    }

    public JwtAccessPrincipal {
        if (principalType == null || principalType.isBlank()) {
            principalType = JwtService.PRINCIPAL_APP;
        }
        if (scopes == null) {
            scopes = List.of();
        }
        if (products == null) {
            products = List.of();
        }
    }

    public boolean hasScope(String scopeCode) {
        return scopes != null && scopes.stream().anyMatch(scope -> scope.equals(scopeCode));
    }

    public boolean isCustomer() {
        return JwtService.PRINCIPAL_CUSTOMER.equals(principalType);
    }

    public boolean isWaiter() {
        return JwtService.PRINCIPAL_WAITER.equals(principalType);
    }
}
