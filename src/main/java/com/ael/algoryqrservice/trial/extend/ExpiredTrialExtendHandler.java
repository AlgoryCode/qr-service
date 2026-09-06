package com.ael.algoryqrservice.trial.extend;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.enums.PurchaseLogAction;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.trial.domain.TrialLifecycle;
import com.ael.algoryqrservice.trial.domain.TrialSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExpiredTrialExtendHandler implements TrialExtendHandler {

    private final PurchaseRepository purchaseRepository;
    private final TrialPurchaseExtender trialPurchaseExtender;

    @Override
    public TrialLifecycle supports() {
        return TrialLifecycle.EXPIRED;
    }

    @Override
    public Purchase extend(Long userId, TrialSnapshot snapshot, int days) {
        Purchase trial = purchaseRepository
                .findFirstByUserIdAndPurchaseTypeOrderByPurchasedAtDesc(userId, PurchaseType.TRIAL)
                .orElseThrow(() -> new BadRequestException("Deneme kaydi bulunamadi"));
        return trialPurchaseExtender.addDays(trial, days, PurchaseLogAction.TRIAL_REACTIVATED);
    }
}
