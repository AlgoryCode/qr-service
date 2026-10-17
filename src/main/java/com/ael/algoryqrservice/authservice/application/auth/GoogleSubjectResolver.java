package com.ael.algoryqrservice.authservice.application.auth;

import com.ael.algoryqrservice.authservice.exception.BadRequestException;
import com.ael.algoryqrservice.authservice.util.ClientInfo;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class GoogleSubjectResolver {

    private final Map<String, GoogleSubjectStrategy> strategies;

    public GoogleSubjectResolver(List<GoogleSubjectStrategy> strategyList) {
        this.strategies = strategyList.stream()
                .collect(Collectors.toMap(strategy -> strategy.audience().toUpperCase(Locale.ROOT), Function.identity()));
    }

    public AuthSubject login(String audience, GoogleOidcIdentity identity) {
        return require(audience).login(identity);
    }

    public AuthSubject register(String audience, GoogleOidcIdentity identity, ClientInfo clientInfo, String provider) {
        return require(audience).register(identity, clientInfo, provider);
    }

    public GoogleSubjectStrategy require(String audience) {
        String key = audience == null || audience.isBlank()
                ? AuthSubject.TYPE_MERCHANT
                : audience.trim().toUpperCase(Locale.ROOT);
        GoogleSubjectStrategy strategy = strategies.get(key);
        if (strategy == null) {
            throw new BadRequestException("Unknown Google audience");
        }
        return strategy;
    }
}
