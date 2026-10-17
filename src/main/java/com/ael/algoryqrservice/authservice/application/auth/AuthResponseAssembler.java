package com.ael.algoryqrservice.authservice.application.auth;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class AuthResponseAssembler {

    public Map<String, Object> assemble(SessionIssuer.IssuedTokens tokens, AuthSubject subject) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("accessToken", tokens.accessToken());
        body.put("refreshToken", tokens.refreshToken());
        Long merchantId = merchantId(subject);
        if (merchantId != null) {
            body.put("merchantId", merchantId);
        }
        return body;
    }

    private static Long merchantId(AuthSubject subject) {
        if (subject == null) {
            return null;
        }
        if (AuthSubject.TYPE_ADMIN.equals(subject.subjectType())
                || AuthSubject.TYPE_CUSTOMER.equals(subject.subjectType())) {
            return null;
        }
        if (subject.merchantId() != null) {
            return subject.merchantId();
        }
        if (AuthSubject.TYPE_MERCHANT.equals(subject.subjectType())) {
            return subject.subjectId();
        }
        return null;
    }
}
