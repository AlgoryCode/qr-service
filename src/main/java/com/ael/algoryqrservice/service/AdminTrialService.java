package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogPackages;
import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.NotFoundException;
import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.PlanPackageItem;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.dto.AdminUserDtos;
import com.ael.algoryqrservice.model.enums.BillingPeriod;
import com.ael.algoryqrservice.model.enums.PaymentStyle;
import com.ael.algoryqrservice.model.enums.PurchaseLogAction;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.repository.PlanPackageRepository;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.repository.UserRepository;
import com.ael.algoryqrservice.service.entitlement.PackageEntitlementWriter;
import com.ael.algoryqrservice.service.entitlement.PurchaseExpiryService;
import com.ael.algoryqrservice.util.AppTime;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AdminTrialService {

    private static final int MIN_DAYS = 1;
    private static final int MAX_DAYS = 365;

    private final UserRepository userRepository;
    private final PurchaseRepository purchaseRepository;
    private final PlanPackageRepository packageRepository;
    private final PackageEntitlementWriter entitlementWriter;
    private final PurchaseExpiryService purchaseExpiryService;
    private final PackageActivationService packageActivationService;
    private final UserTrialService userTrialService;
    private final PurchaseLogService purchaseLogService;

    @Transactional
    public AdminUserDtos.ExtendTrialResponse extendTrial(Long userId, int days) {
        validateDays(days);
        userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Kullanici bulunamadi"));
        rejectIfHasUsablePaidPackage(userId);

        Purchase trial = purchaseRepository
                .findFirstByUserIdAndPurchaseTypeOrderByPurchasedAtDesc(userId, PurchaseType.TRIAL)
                .map(existing -> extendExistingTrial(existing, days))
                .orElseGet(() -> grantNewUltimateTrial(userId, days));

        userTrialService.resetTrialEligibility(userId);
        packageActivationService.activatePurchasedPackage(trial);
        packageActivationService.ensureSubscriptionState(userId);

        purchaseLogService.log(
                trial.getId(),
                userId,
                PurchaseLogAction.TRIAL_EXTENDED,
                "Admin deneme suresi " + days + " gun eklendi. Yeni bitis: " + trial.getExpiresAt()
        );

        return AdminUserDtos.ExtendTrialResponse.builder()
                .purchaseId(trial.getId())
                .packageName(trial.getPackageName())
                .expiresAt(trial.getExpiresAt())
                .daysAdded(days)
                .build();
    }

    private Purchase extendExistingTrial(Purchase trial, int days) {
        LocalDateTime now = AppTime.nowLocal();
        LocalDateTime base = trial.getStatus() == PurchaseStatus.ACTIVE
                && trial.getExpiresAt() != null
                && trial.getExpiresAt().isAfter(now)
                ? trial.getExpiresAt()
                : now;

        trial.setStatus(PurchaseStatus.ACTIVE);
        trial.setExpiresAt(base.plusDays(days));
        if (trial.getStartsAt() == null) {
            trial.setStartsAt(now);
        }
        purchaseRepository.save(trial);
        entitlementWriter.synchronizePeriod(trial);

        if (trial.getPackageId() != null) {
            packageRepository.findByIdWithItems(trial.getPackageId())
                    .ifPresent(planPackage -> entitlementWriter.ensureEntitlementsForPackage(trial, planPackage));
        }
        return trial;
    }

    private Purchase grantNewUltimateTrial(Long userId, int days) {
        PlanPackage ultimate = packageRepository.findByCode(CatalogPackages.ULTIMATE_PACKAGE)
                .flatMap(existing -> packageRepository.findByIdWithItems(existing.getId()))
                .filter(planPackage -> planPackage.isActive() && !planPackage.isSystemManaged())
                .orElseThrow(() -> new BadRequestException("Ultimate paketi bulunamadi veya aktif degil"));

        LocalDateTime startsAt = AppTime.nowLocal();
        Purchase purchase;
        try {
            purchase = purchaseRepository.saveAndFlush(Purchase.builder()
                    .userId(userId)
                    .packageId(ultimate.getId())
                    .packageCode(ultimate.getCode())
                    .packageName(ultimate.getName())
                    .price(BigDecimal.ZERO)
                    .currency(ultimate.getCurrency())
                    .purchaseType(PurchaseType.TRIAL)
                    .paymentStyle(PaymentStyle.ONE_TIME)
                    .billingPeriod(BillingPeriod.MONTHLY)
                    .billingIntervalMonths(BillingPeriod.MONTHLY.intervalMonths())
                    .status(PurchaseStatus.ACTIVE)
                    .startsAt(startsAt)
                    .expiresAt(startsAt.plusDays(days))
                    .build());
        } catch (DataIntegrityViolationException exception) {
            Purchase existing = purchaseRepository
                    .findFirstByUserIdAndPurchaseTypeOrderByPurchasedAtDesc(userId, PurchaseType.TRIAL)
                    .orElseThrow(() -> new BadRequestException("Deneme kaydi olusturulamadi"));
            return extendExistingTrial(existing, days);
        }

        for (PlanPackageItem item : ultimate.getItems()) {
            entitlementWriter.grant(
                    purchase,
                    item.getProduct().getId(),
                    item.getProduct().getCode(),
                    item.getQuantity(),
                    item.isUnlimited()
            );
        }
        return purchase;
    }

    private void validateDays(int days) {
        if (days < MIN_DAYS || days > MAX_DAYS) {
            throw new BadRequestException("days " + MIN_DAYS + " ile " + MAX_DAYS + " arasinda olmalidir");
        }
    }

    private void rejectIfHasUsablePaidPackage(Long userId) {
        purchaseExpiryService.expireDueForUser(userId);
        boolean hasPaid = purchaseRepository.findByUserIdAndStatus(userId, PurchaseStatus.ACTIVE).stream()
                .anyMatch(purchase -> purchase.isUsable()
                        && purchase.getPurchaseType() == PurchaseType.PAID);
        if (hasPaid) {
            throw new BadRequestException("Aktif ucretli paket varken deneme uzatilamaz");
        }
    }
}
