package com.ael.algoryqrservice.security;

import java.util.List;

public record JwtAccessPrincipal(
        Long userId,
        List<String> scopes,
        List<String> products,
        String activePackage
) {
    public boolean hasScope(String scopeCode) {
        return scopes != null && scopes.stream().anyMatch(scope -> scope.equals(scopeCode));
    }
}
