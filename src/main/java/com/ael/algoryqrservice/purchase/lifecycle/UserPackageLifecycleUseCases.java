package com.ael.algoryqrservice.purchase.lifecycle;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.NotFoundException;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.dto.AdminUserDtos;
import com.ael.algoryqrservice.model.enums.PurchaseLogAction;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.repository.TrialLogRepository;
import com.ael.algoryqrservice.repository.UserRepository;
import com.ael.algoryqrservice.service.PackageActivationService;
import com.ael.algoryqrservice.service.PurchaseLogService;
import com.ael.algoryqrservice.service.entitlement.PurchaseExpiryService;
import com.ael.algoryqrservice.trial.TrialUseCases;
import com.ael.algoryqrservice.util.AppTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserPackageLifecycleUseCases {

    private static final int MIN_DAYS = 1;
    private static final int MAX_DAYS = 3650;

    private final UserRepository userRepository;
    private final TrialLogRepository trialLogRepository;
    private final ActivePackageResolver activePackageResolver;
    private final RemoteSubscriptionCanceller remoteSubscriptionCanceller;
    private final PackageAccessRestorer packageAccessRestorer;
    private final PackagePeriodExtender packagePeriodExtender;
    private final TrialUseCases trialUseCases;
    private final PurchaseExpiryService purchaseExpiryService;
    private final PackageActivationService packageActivationService;
    private final PurchaseLogService purchaseLogService;

    @Transactional
    public AdminUserDtos.PackageLifecycleResponse deactivate(Long userId) {
        requireUser(userId);
        Purchase purchase = resolveActiveOrThrow(userId);
        cutAccessNow(purchase);
        remoteSubscriptionCanceller.cancelIfNeeded(purchase);
        purchaseExpiryService.expire(purchase);
        syncAccess(userId);
        purchaseLogService.log(
                purchase.getId(),
                userId,
                PurchaseLogAction.PURCHASE_DEACTIVATED,
                "Admin paket pasiflestirdi. Bitis: " + purchase.getExpiresAt()
        );
        return toResponse(purchase, null);
    }

    @Transactional
    public AdminUserDtos.PackageLifecycleResponse reactivate(Long userId, int days) {
        requireUser(userId);
        validateDays(days);
        Purchase purchase = resolveExpiredOrThrow(userId);
        Purchase restored = packageAccessRestorer.restoreActive(purchase, days);
        packageActivationService.activatePurchasedPackage(restored);
        syncAccess(userId);
        purchaseLogService.log(
                restored.getId(),
                userId,
                PurchaseLogAction.PURCHASE_REACTIVATED,
                "Admin paket aktiflestirdi. " + days + " gun. Bitis: " + restored.getExpiresAt()
        );
        return toResponse(restored, days);
    }

    @Transactional
    public AdminUserDtos.PackageLifecycleResponse extend(Long userId, int days) {
        requireUser(userId);
        validateDays(days);
        Optional<Purchase> active = activePackageResolver.currentActive(userId);
        if (active.isPresent()) {
            Purchase extended = packagePeriodExtender.extend(active.get(), days);
            syncAccess(userId);
            return toResponse(extended, days);
        }
        if (!trialLogRepository.existsByUserId(userId)) {
            throw new BadRequestException("Uzatilacak paket bulunamadi");
        }
        AdminUserDtos.ExtendTrialResponse trial = trialUseCases.extend(userId, days);
        return AdminUserDtos.PackageLifecycleResponse.builder()
                .purchaseId(trial.getPurchaseId())
                .packageName(trial.getPackageName())
                .status(PurchaseStatus.ACTIVE)
                .expiresAt(trial.getExpiresAt())
                .daysAdded(days)
                .build();
    }

    private void requireUser(Long userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Kullanici bulunamadi"));
    }

    private Purchase resolveActiveOrThrow(Long userId) {
        Optional<Purchase> active = activePackageResolver.currentActive(userId);
        if (!UserPackageLifecyclePolicy.canDeactivate(active)) {
            throw new BadRequestException("Aktif paket bulunamadi");
        }
        return active.orElseThrow();
    }

    private Purchase resolveExpiredOrThrow(Long userId) {
        Optional<Purchase> expired = activePackageResolver.latestExpiredSubscriptionLike(userId);
        if (!UserPackageLifecyclePolicy.canReactivate(expired)) {
            throw new BadRequestException("Aktiflestirilecek paket bulunamadi");
        }
        return expired.orElseThrow();
    }

    private void cutAccessNow(Purchase purchase) {
        purchase.setExpiresAt(AppTime.nowLocal());
    }

    private void syncAccess(Long userId) {
        packageActivationService.ensureSubscriptionState(userId);
    }

    private void validateDays(int days) {
        if (days < MIN_DAYS || days > MAX_DAYS) {
            throw new BadRequestException("days " + MIN_DAYS + " ile " + MAX_DAYS + " arasinda olmalidir");
        }
    }

    private AdminUserDtos.PackageLifecycleResponse toResponse(Purchase purchase, Integer daysAdded) {
        return AdminUserDtos.PackageLifecycleResponse.builder()
                .purchaseId(purchase.getId())
                .packageName(purchase.getPackageName())
                .status(purchase.getStatus())
                .expiresAt(purchase.getExpiresAt())
                .daysAdded(daysAdded)
                .build();
    }
}
