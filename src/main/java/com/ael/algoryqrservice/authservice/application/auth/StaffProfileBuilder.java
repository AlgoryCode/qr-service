package com.ael.algoryqrservice.authservice.application.auth;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
class StaffProfileBuilder implements RoleResponseFactory.ProfileBuilder {
    @Override
    public String roleCode() {
        return "WAITER";
    }

    @Override
    public Map<String, Object> build(AuthSubject subject) {
        return staffProfile(subject);
    }

    static Map<String, Object> staffProfile(AuthSubject subject) {
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("staffId", subject.subjectId());
        profile.put("merchantId", subject.merchantId());
        profile.put("branchId", subject.branchId());
        profile.put("role", subject.roleCode());
        profile.put("displayName", subject.displayName());
        profile.put("username", subject.loginIdentifier());
        return profile;
    }
}
