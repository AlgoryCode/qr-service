package com.ael.algoryqrservice.authservice.application.auth;

import com.ael.algoryqrservice.authservice.util.ClientInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class RegisterAccountUseCase {

    private final RegisterMerchantUseCase registerMerchantUseCase;

    public Map<String, Object> register(
            String email,
            String password,
            String firstName,
            String lastName,
            String phone,
            ClientInfo clientInfo
    ) {
        return registerMerchantUseCase.register(email, password, firstName, lastName, phone, clientInfo);
    }
}
