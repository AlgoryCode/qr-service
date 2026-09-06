package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.dto.AdminUserDtos;
import com.ael.algoryqrservice.trial.TrialUseCases;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminTrialService {

    private final TrialUseCases trialUseCases;

    public AdminUserDtos.ExtendTrialResponse extendTrial(Long userId, int days) {
        return trialUseCases.extend(userId, days);
    }

    public AdminUserDtos.EndTrialResponse endTrial(Long userId) {
        return trialUseCases.end(userId);
    }
}
