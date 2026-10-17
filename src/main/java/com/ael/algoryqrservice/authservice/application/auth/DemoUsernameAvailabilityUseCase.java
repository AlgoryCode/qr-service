package com.ael.algoryqrservice.authservice.application.auth;

import com.ael.algoryqrservice.authservice.infrastructure.persistence.repository.DemoAccountJpaRepository;
import com.ael.algoryqrservice.merchantservice.infrastructure.persistence.repository.MerchantJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.ael.algoryqrservice.authservice.config.AuthTransactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DemoUsernameAvailabilityUseCase {

    private final DemoAccountJpaRepository demoAccountRepository;
    private final MerchantJpaRepository merchantRepository;

    @AuthTransactional(readOnly = true)
    public Map<String, Object> check(String rawUsername) {
        String username = DemoAccountSupport.normalizeUsername(rawUsername);
        String email = DemoAccountSupport.demoEmailForUsername(username);
        boolean taken = demoAccountRepository.existsByUsernameIgnoreCase(username)
                || merchantRepository.existsByEmailIgnoreCaseAndDeletedAtIsNull(email);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", username);
        body.put("available", !taken);
        return body;
    }
}
