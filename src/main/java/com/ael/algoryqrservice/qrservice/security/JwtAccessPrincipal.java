package com.ael.algoryqrservice.qrservice.security;

import com.ael.algoryqrservice.qrservice.account.service.JwtService;

import java.util.List;

public record JwtAccessPrincipal(
        Long staffId,
        Long merchantId,
        Long customerId,
        Long dashboardUserId,
        List<String> scopes,
        List<String> products,
        String activePackage,
        String principalType,
        Long branchId
) {
    public JwtAccessPrincipal(
            List<String> scopes,
            List<String> products,
            String activePackage
    ) {
        this(null, null, null, null, scopes, products, activePackage, JwtService.PRINCIPAL_APP, null);
    }

    public JwtAccessPrincipal(
            List<String> scopes,
            List<String> products,
            String activePackage,
            String principalType
    ) {
        this(null, null, null, null, scopes, products, activePackage, principalType, null);
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
