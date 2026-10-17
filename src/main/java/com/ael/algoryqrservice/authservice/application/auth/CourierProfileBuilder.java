package com.ael.algoryqrservice.authservice.application.auth;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
class CourierProfileBuilder implements RoleResponseFactory.ProfileBuilder {
    @Override
    public String roleCode() {
        return "COURIER";
    }

    @Override
    public Map<String, Object> build(AuthSubject subject) {
        return StaffProfileBuilder.staffProfile(subject);
    }
}
