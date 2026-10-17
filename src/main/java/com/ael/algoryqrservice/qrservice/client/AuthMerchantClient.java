package com.ael.algoryqrservice.qrservice.client;

import com.ael.algoryqrservice.merchantservice.application.FindMerchantUseCase;
import com.ael.algoryqrservice.qrservice.exception.NotFoundException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AuthMerchantClient {

    private final FindMerchantUseCase findMerchantUseCase;

    public Optional<Long> findActiveMerchantIdByEmail(String email) {
        return findMerchantUseCase.findActiveMerchantIdByEmail(email);
    }

    public MerchantView findMerchant(Long merchantId) {
        if (merchantId == null) {
            throw new NotFoundException("Merchant bulunamadi");
        }
        try {
            FindMerchantUseCase.MerchantResponse response = findMerchantUseCase.findMerchant(merchantId);
            return new MerchantView(response.id(), response.status());
        } catch (RuntimeException exception) {
            throw new NotFoundException("Merchant bulunamadi");
        }
    }

    public Optional<MerchantProfileView> findMerchantProfile(Long merchantId) {
        return findMerchantUseCase.findProfile(merchantId)
                .map(profile -> new MerchantProfileView(
                        profile.id(),
                        profile.firstName(),
                        profile.lastName(),
                        profile.email(),
                        profile.phone(),
                        profile.provider()
                ));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MerchantView(Long id, String status) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MerchantProfileView(
            Long id,
            String firstName,
            String lastName,
            String email,
            String phone,
            String provider
    ) {
    }
}
