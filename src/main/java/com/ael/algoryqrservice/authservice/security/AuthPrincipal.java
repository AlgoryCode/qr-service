package com.ael.algoryqrservice.authservice.security;

import com.ael.algoryqrservice.authservice.application.auth.AuthSubject;

import java.util.List;
import java.util.UUID;

public record AuthPrincipal(
        String subjectType,
        String loginIdentifier,
        List<String> roles,
        List<String> scopes,
        Long merchantId,
        Long staffId,
        Long customerId,
        Long authSubjectId,
        Long branchId,
        UUID sessionId
) {
    public Long resolveSubjectId() {
        if (AuthSubject.TYPE_STAFF.equals(subjectType)) {
            return staffId;
        }
        if (AuthSubject.TYPE_MERCHANT.equals(subjectType)) {
            return merchantId;
        }
        if (AuthSubject.TYPE_ADMIN.equals(subjectType)) {
            return authSubjectId;
        }
        if (AuthSubject.TYPE_CUSTOMER.equals(subjectType)) {
            return customerId;
        }
        return merchantId != null ? merchantId : staffId;
    }
}
