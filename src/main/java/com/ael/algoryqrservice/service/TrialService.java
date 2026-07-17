package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.PlanPackageItem;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.dto.TrialDtos;
import com.ael.algoryqrservice.model.enums.PaymentStyle;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.repository.PlanPackageRepository;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TrialService {

    private final PurchaseRepository purchaseRepository;
    private final PlanPackageRepository packageRepository;
    private final EntitlementService entitlementService;
    private final PackageActivationService packageActivationService;

    @Transactional
    public TrialDtos.Status startDigitalMenuPro(Long userId) {
        if (purchaseRepository.existsByUserIdAndPurchaseType(userId, PurchaseType.TRIAL)) {
            throw new BadRequestException("Deneme hakkı daha önce kullanılmış");
        }
        PlanPackage planPackage = packageRepository.findFirstByTrialEligibleTrueAndActiveTrueOrderByPriorityDesc()
                .orElseThrow(() -> new BadRequestException("Deneme için uygun paket bulunamadı"));
        LocalDateTime startsAt = LocalDateTime.now();
        Purchase purchase;
        try {
            purchase = purchaseRepository.saveAndFlush(Purchase.builder()
                    .userId(userId)
                    .packageId(planPackage.getId())
                    .packageCode(planPackage.getCode())
                    .packageName(planPackage.getName())
                    .price(BigDecimal.ZERO)
                    .currency(planPackage.getCurrency())
                    .purchaseType(PurchaseType.TRIAL)
                    .paymentStyle(PaymentStyle.ONE_TIME)
                    .status(PurchaseStatus.ACTIVE)
                    .startsAt(startsAt)
                    .expiresAt(startsAt.plusDays(planPackage.getValidityDays()))
                    .build());
        } catch (DataIntegrityViolationException exception) {
            throw new BadRequestException("Deneme hakkı daha önce kullanılmış");
        }
        packageActivationService.activatePurchasedPackage(purchase);
        for (PlanPackageItem item : planPackage.getItems()) {
            entitlementService.grant(
                    purchase,
                    item.getProduct().getId(),
                    item.getProduct().getCode(),
                    item.getQuantity(),
                    item.isUnlimited()
            );
        }
        return statusOf(purchase);
    }

    @Transactional
    public TrialDtos.Status status(Long userId) {
        Purchase purchase = purchaseRepository
                .findFirstByUserIdAndPurchaseTypeOrderByPurchasedAtDesc(userId, PurchaseType.TRIAL)
                .orElse(null);
        if (purchase == null) {
            return new TrialDtos.Status(TrialDtos.Lifecycle.AVAILABLE, null, null, null);
        }
        if (purchase.getStatus() == PurchaseStatus.ACTIVE && purchase.isExpiredByDate()) {
            entitlementService.expirePurchase(purchase);
            packageActivationService.ensureFreePackage(userId);
        }
        return statusOf(purchase);
    }

    private TrialDtos.Status statusOf(Purchase purchase) {
        TrialDtos.Lifecycle lifecycle = purchase.getStatus() == PurchaseStatus.ACTIVE && !purchase.isExpiredByDate()
                ? TrialDtos.Lifecycle.ACTIVE
                : TrialDtos.Lifecycle.TRIAL_EXPIRED;
        return new TrialDtos.Status(lifecycle, purchase.getId(), purchase.getStartsAt(), purchase.getExpiresAt());
    }
}
