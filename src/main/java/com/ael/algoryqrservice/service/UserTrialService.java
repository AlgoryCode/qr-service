package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserTrialService {

    private final UserRepository userRepository;
    private final PurchaseRepository purchaseRepository;

    @Transactional(readOnly = true)
    public boolean hasUsedTrial(User user) {
        if (user == null) {
            return false;
        }
        if (user.getTrialEndDate() != null) {
            return true;
        }
        return user.isTrialUsed();
    }

    @Transactional(readOnly = true)
    public boolean hasTrialPurchase(Long userId) {
        if (userId == null) {
            return false;
        }
        return purchaseRepository.existsByUserIdAndPurchaseType(userId, PurchaseType.TRIAL);
    }

    @Transactional
    public void resetTrialEligibility(Long userId) {
        if (userId == null) {
            return;
        }
        userRepository.findById(userId).ifPresent(user -> {
            user.setTrialUsed(false);
            user.setTrialEndDate(null);
            userRepository.save(user);
        });
    }

    @Transactional
    public void markTrialCompleted(Long userId, LocalDateTime endDate) {
        if (userId == null) {
            return;
        }
        userRepository.findById(userId).ifPresent(user -> {
            if (user.getTrialEndDate() != null) {
                if (!user.isTrialUsed()) {
                    user.setTrialUsed(true);
                    userRepository.save(user);
                }
                return;
            }
            user.setTrialEndDate(endDate != null ? endDate : LocalDateTime.now());
            user.setTrialUsed(true);
            userRepository.save(user);
        });
    }
}
