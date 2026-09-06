package com.ael.algoryqrservice.trial;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.NotFoundException;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.dto.AdminUserDtos;
import com.ael.algoryqrservice.model.enums.PurchaseLogAction;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.repository.UserRepository;
import com.ael.algoryqrservice.service.PackageActivationService;
import com.ael.algoryqrservice.service.PurchaseLogService;
import com.ael.algoryqrservice.service.entitlement.PurchaseExpiryService;
import com.ael.algoryqrservice.trial.domain.TrialLifecycle;
import com.ael.algoryqrservice.trial.domain.TrialPolicy;
import com.ael.algoryqrservice.trial.domain.TrialSnapshot;
import com.ael.algoryqrservice.trial.extend.TrialExtendHandlerRegistry;
import com.ael.algoryqrservice.util.AppTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TrialUseCases {

    private static final int MIN_DAYS = 1;
    private static final int MAX_DAYS = 365;

    private final UserRepository userRepository;
    private final PurchaseRepository purchaseRepository;
    private final TrialSnapshotQuery trialSnapshotQuery;
    private final TrialExtendHandlerRegistry trialExtendHandlerRegistry;
    private final PackageActivationService packageActivationService;
    private final PurchaseExpiryService purchaseExpiryService;
    private final PurchaseLogService purchaseLogService;

    @Transactional
    public AdminUserDtos.ExtendTrialResponse extend(Long userId, int days) {
        validateDays(days);
        userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Kullanici bulunamadi"));

        TrialSnapshot snapshot = trialSnapshotQuery.forUser(userId);
        if (!TrialPolicy.canExtend(snapshot)) {
            throw new BadRequestException("Aktif ucretli paket varken deneme uzatilamaz");
        }

        Purchase trial = trialExtendHandlerRegistry.extend(userId, snapshot, days);
        packageActivationService.activatePurchasedPackage(trial);
        packageActivationService.ensureSubscriptionState(userId);

        return AdminUserDtos.ExtendTrialResponse.builder()
                .purchaseId(trial.getId())
                .packageName(trial.getPackageName())
                .expiresAt(trial.getExpiresAt())
                .daysAdded(days)
                .build();
    }

    @Transactional
    public AdminUserDtos.EndTrialResponse end(Long userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Kullanici bulunamadi"));

        TrialSnapshot snapshot = trialSnapshotQuery.forUser(userId);
        if (!TrialPolicy.canEnd(snapshot)) {
            throw new BadRequestException("Aktif deneme bulunamadi");
        }

        Purchase trial = purchaseRepository
                .findFirstByUserIdAndPurchaseTypeOrderByPurchasedAtDesc(userId, PurchaseType.TRIAL)
                .orElseThrow(() -> new BadRequestException("Aktif deneme bulunamadi"));

        trial.setExpiresAt(AppTime.nowLocal());
        purchaseExpiryService.expire(trial);
        packageActivationService.ensureSubscriptionState(userId);
        purchaseLogService.log(
                trial.getId(),
                userId,
                PurchaseLogAction.TRIAL_ENDED,
                "Admin deneme surecini bitirdi. Bitis: " + trial.getExpiresAt()
        );

        return AdminUserDtos.EndTrialResponse.builder()
                .purchaseId(trial.getId())
                .packageName(trial.getPackageName())
                .expiresAt(trial.getExpiresAt())
                .build();
    }

    @Transactional
    public TrialSnapshot snapshot(Long userId) {
        return trialSnapshotQuery.forUser(userId);
    }

    @Transactional
    public void assertCanStart(Long userId) {
        TrialSnapshot snapshot = trialSnapshotQuery.forUser(userId);
        if (TrialPolicy.canStart(snapshot)) {
            return;
        }
        if (snapshot.lifecycle() == TrialLifecycle.BLOCKED_BY_PAID) {
            throw new BadRequestException("Aktif ucretli paket varken deneme baslatilamaz");
        }
        throw new BadRequestException("Deneme hakki daha once kullanilmis");
    }

    private void validateDays(int days) {
        if (days < MIN_DAYS || days > MAX_DAYS) {
            throw new BadRequestException("days " + MIN_DAYS + " ile " + MAX_DAYS + " arasinda olmalidir");
        }
    }
}
