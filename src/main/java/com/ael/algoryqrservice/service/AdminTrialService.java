package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.dto.AdminUserDtos;
import com.ael.algoryqrservice.trial.TrialUseCases;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AdminTrialService {

    private final TrialUseCases trialUseCases;

    public Object updateTrial(Long userId, AdminUserDtos.TrialUpdateRequest request) {
        if (request.getStatus() != null && "ENDED".equals(request.getStatus().toUpperCase(Locale.ROOT))) {
            return endTrial(userId);
        }
        if (request.getDays() == null) {
            throw new BadRequestException("days veya status=ENDED gerekli");
        }
        return extendTrial(userId, request.getDays());
    }

    public AdminUserDtos.ExtendTrialResponse extendTrial(Long userId, int days) {
        return trialUseCases.extend(userId, days);
    }

    public AdminUserDtos.EndTrialResponse endTrial(Long userId) {
        return trialUseCases.end(userId);
    }
}
