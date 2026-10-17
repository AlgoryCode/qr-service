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
        return body;
    }
}
