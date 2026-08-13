package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.Customer;
import com.ael.algoryqrservice.model.dto.CustomerAuthDtos;
import com.ael.algoryqrservice.model.dto.LogoutRequest;
import com.ael.algoryqrservice.model.dto.RefreshTokenRequest;
import com.ael.algoryqrservice.model.enums.AuthProvider;
import com.ael.algoryqrservice.repository.CustomerRepository;
import com.ael.algoryqrservice.util.ClientInfo;
import com.ael.algoryqrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerAuthService {

    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final CustomerSessionService customerSessionService;
    private final CustomerAccountService customerAccountService;
    private final JwtService jwtService;
    private final SecurityUtils securityUtils;

    @Transactional
    public CustomerAuthDtos.CustomerAuthResponse register(
            CustomerAuthDtos.CustomerRegisterRequest request,
            ClientInfo clientInfo
    ) {
        if (!request.getPassword().equals(request.getPasswordConfirm())) {
            throw new BadRequestException("Şifreler eşleşmiyor");
        }

        String email = request.getEmail().trim().toLowerCase();
        if (customerRepository.existsByEmail(email)) {
            throw new BadRequestException("Bu e-posta adresi zaten kayıtlı");
        }

        Customer customer = Customer.builder()
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName() != null ? request.getLastName().trim() : null)
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .provider(AuthProvider.BASIC)
                .build();

        Customer saved = customerRepository.save(customer);

        if (request.getMenuId() != null) {
            customerAccountService.joinMembership(saved.getId(), request.getMenuId());
        }

        return createAuthResponse(saved, clientInfo);
    }

    @Transactional
    public CustomerAuthDtos.CustomerAuthResponse login(
            CustomerAuthDtos.CustomerLoginRequest request,
            ClientInfo clientInfo
    ) {
        Customer customer = authenticate(request);

        if (request.getMenuId() != null) {
            customerAccountService.joinMembership(customer.getId(), request.getMenuId());
        }

        return createAuthResponse(customer, clientInfo);
    }

    private Customer authenticate(CustomerAuthDtos.CustomerLoginRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        Customer customer = customerRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Geçersiz kimlik bilgileri"));
        if (customer.getProvider() != AuthProvider.BASIC) {
            throw new BadCredentialsException("Geçersiz kimlik bilgileri");
        }
        if (customer.getPassword() == null
                || !passwordEncoder.matches(request.getPassword(), customer.getPassword())) {
            throw new BadCredentialsException("Geçersiz kimlik bilgileri");
        }
        return customer;
    }

    private CustomerAuthDtos.CustomerAuthResponse createAuthResponse(Customer customer, ClientInfo clientInfo) {
        CustomerSessionService.SessionTokens tokens = customerSessionService.createSession(customer, clientInfo);
        return customerSessionService.buildCustomerAuthResponse(
                tokens.accessToken(),
                tokens.refreshToken(),
                customer.getId()
        );
    }

    public CustomerAuthDtos.CustomerAuthResponse refresh(RefreshTokenRequest request) {
        return customerSessionService.refresh(request.getRefreshToken());
    }

    @Transactional
    public void logout(LogoutRequest request, String accessToken) {
        if (accessToken != null && !accessToken.isBlank()) {
            var revoked = jwtService.extractSessionIdIfSignatureValid(accessToken)
                    .map(sessionId -> {
                        customerSessionService.revokeByAccessSessionId(sessionId);
                        return true;
                    })
                    .orElse(false);
            if (revoked) {
                return;
            }
        }

        if (request != null && request.getRefreshToken() != null && !request.getRefreshToken().isBlank()) {
            customerSessionService.revokeByRefreshToken(request.getRefreshToken());
            return;
        }

        throw new BadRequestException("Çıkış için geçerli access token veya refresh token gerekli");
    }

    public Customer getCurrentCustomer() {
        return securityUtils.getCurrentCustomer();
    }
}
