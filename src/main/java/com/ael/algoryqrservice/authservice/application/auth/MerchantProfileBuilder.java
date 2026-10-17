package com.ael.algoryqrservice.authservice.application.auth;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
class MerchantProfileBuilder implements RoleResponseFactory.ProfileBuilder {
    @Override
    public String roleCode() {
        return "MERCHANT";
    }

    @Override
    public Map<String, Object> build(AuthSubject subject) {
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("merchantId", subject.subjectId());
        profile.put("role", subject.roleCode());
        profile.put("displayName", subject.displayName());
        profile.put("email", subject.loginIdentifier());
        return profile;
    }
}
