package com.ael.algoryqrservice.service;

import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserDebtService {

    public Optional<DebtView> findDebt(Long userId) {
        return Optional.empty();
    }

    public record DebtView(Long userId, String status) {
    }
}
