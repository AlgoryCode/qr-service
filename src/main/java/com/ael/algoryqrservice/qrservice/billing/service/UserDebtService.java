package com.ael.algoryqrservice.qrservice.billing.service;

import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserDebtService {

    public Optional<DebtView> findDebt(Long merchantId) {
        return Optional.empty();
    }

    public record DebtView(Long merchantId, String status) {
    }
}
