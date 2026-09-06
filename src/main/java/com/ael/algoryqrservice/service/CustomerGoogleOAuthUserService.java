package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.UnauthorizedException;
import com.ael.algoryqrservice.model.Customer;
import com.ael.algoryqrservice.model.dto.GoogleOidcIdentity;
import com.ael.algoryqrservice.model.enums.AuthProvider;
import com.ael.algoryqrservice.model.enums.GoogleAuthIntent;
import com.ael.algoryqrservice.repository.CustomerRepository;
import com.ael.algoryqrservice.util.ClientInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CustomerGoogleOAuthUserService {

    static final String BASIC_ACCOUNT_GOOGLE_LOGIN_MESSAGE =
            "Bu e-posta adresi e-posta/şifre ile kayıtlı. Lütfen e-posta ve şifre ile giriş yapın.";

    private final CustomerRepository customerRepository;

    @Transactional
    public Customer resolve(GoogleAuthIntent intent, GoogleOidcIdentity identity, ClientInfo clientInfo) {
        requireVerifiedEmail(identity);
        return switch (intent) {
            case CUSTOMER_LOGIN -> login(identity);
            case CUSTOMER_REGISTER -> register(identity, clientInfo);
            case LOGIN, REGISTER -> throw new BadRequestException("Geçersiz müşteri Google kimlik doğrulama amacı");
        };
    }

    private Customer login(GoogleOidcIdentity identity) {
        Optional<Customer> googleCustomer = customerRepository.findByProviderAndProviderSubject(
                AuthProvider.GOOGLE,
                identity.subject()
        );
        if (googleCustomer.isPresent()) {
            return googleCustomer.get();
        }

        Optional<Customer> existingByEmail = customerRepository.findByEmail(identity.email());
        if (existingByEmail.isPresent() && existingByEmail.get().getProvider() != AuthProvider.GOOGLE) {
            throw new BadRequestException(providerConflictMessage(existingByEmail.get().getProvider()));
        }

        throw new UnauthorizedException("Bu e-posta adresi ile Google hesabı kayıtlı değil");
    }

    private Customer register(GoogleOidcIdentity identity, ClientInfo clientInfo) {
        Optional<Customer> existingGoogleCustomer = customerRepository.findByProviderAndProviderSubject(
                AuthProvider.GOOGLE,
                identity.subject()
        );
        if (existingGoogleCustomer.isPresent()) {
            throw new BadRequestException("Bu e-posta adresi zaten kayıtlı");
        }

        Optional<Customer> existingByEmail = customerRepository.findByEmail(identity.email());
        if (existingByEmail.isPresent()) {
            if (existingByEmail.get().getProvider() != AuthProvider.GOOGLE) {
                throw new BadRequestException(providerConflictMessage(existingByEmail.get().getProvider()));
            }
            throw new BadRequestException("Bu e-posta adresi zaten kayıtlı");
        }

        return customerRepository.saveAndFlush(Customer.builder()
                .firstName(resolveName(identity.firstName(), "Customer"))
                .lastName(identity.lastName())
                .email(identity.email())
                .password(null)
                .provider(AuthProvider.GOOGLE)
                .providerSubject(identity.subject())
                .build());
    }

    private void requireVerifiedEmail(GoogleOidcIdentity identity) {
        if (!identity.emailVerified()) {
            throw new UnauthorizedException("Doğrulanmış Google e-posta adresi zorunludur");
        }
    }

    private String resolveName(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    static String providerConflictMessage(AuthProvider provider) {
        if (provider == AuthProvider.BASIC) {
            return BASIC_ACCOUNT_GOOGLE_LOGIN_MESSAGE;
        }
        return "Bu hesap farklı bir giriş yöntemiyle oluşturulmuş";
    }
}
