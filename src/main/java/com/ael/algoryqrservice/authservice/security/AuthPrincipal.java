package com.ael.algoryqrservice.authservice.security;

import java.util.List;
import java.util.UUID;

public record AuthPrincipal(
        Long userId,
        String subjectType,
        String loginIdentifier,
        List<String> roles,
        List<String> scopes,
        Long merchantId,
        Long branchId,
        UUID sessionId
) {
}
