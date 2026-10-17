package com.ael.algoryqrservice.authservice.application.auth;

import java.util.List;

public record AuthSubject(
        String subjectType,
        Long subjectId,
        Long roleId,
        String roleCode,
        String passwordHash,
        String displayName,
        String loginIdentifier,
        String provider,
        Long merchantId,
        Long branchId,
        List<String> scopes
) {
    public static final String TYPE_MERCHANT = "MERCHANT";
    public static final String TYPE_STAFF = "STAFF";
    public static final String TYPE_CUSTOMER = "CUSTOMER";
    public static final String TYPE_ADMIN = "ADMIN";
}
