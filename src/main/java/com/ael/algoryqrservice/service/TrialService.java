package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogPackages;
import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.PlanPackageItem;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.dto.PlanPackageItemResponse;
import com.ael.algoryqrservice.model.dto.PlanPackageResponse;
import com.ael.algoryqrservice.model.dto.TrialDtos;
import com.ael.algoryqrservice.model.enums.PaymentStyle;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.repository.PlanPackageRepository;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TrialService {

    private final PurchaseRepository purchaseRepository;
    private final PlanPackageRepository packageRepository;
    private final UserRepository userRepository;
    private final EntitlementService entitlementService;
    private final PackageActivationService packageActivationService;

    @Transactional
    public TrialDtos.Status start(Long userId, Long packageId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("Kullanici bulunamadi"));
        if (user.isTrialUsed() || purchaseRepository.existsByUserIdAndPurchaseType(userId, PurchaseType.TRIAL)) {
            throw new BadRequestException("Deneme hakki daha once kullanilmis");
        }
        rejectIfHasUsablePaidPackage(userId);

        PlanPackage planPackage = resolveTrialPackage(packageId);
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
            throw new BadRequestException("Deneme hakki daha once kullanilmis");
        }
        user.setTrialUsed(true);
        userRepository.save(user);
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
    public TrialDtos.Status startDigitalMenuPro(Long userId) {
        PlanPackage planPackage = packageRepository.findFirstByTrialEligibleTrueAndActiveTrueOrderByPriorityDesc()
                .orElseThrow(() -> new BadRequestException("Deneme icin uygun paket bulunamadi"));
        return start(userId, planPackage.getId());
    }

    @Transactional
    public TrialDtos.Status status(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        Purchase purchase = purchaseRepository
                .findFirstByUserIdAndPurchaseTypeOrderByPurchasedAtDesc(userId, PurchaseType.TRIAL)
                .orElse(null);
        if (purchase == null) {
            if (user != null && user.isTrialUsed()) {
                return usedUnavailableStatus();
            }
            return availableStatus();
        }
        if (purchase.getStatus() == PurchaseStatus.ACTIVE && purchase.isExpiredByDate()) {
            entitlementService.expirePurchase(purchase);
            packageActivationService.ensureFreePackage(userId);
        }
        return statusOf(purchase);
    }

    @Transactional(readOnly = true)
    public List<PlanPackageResponse> listEligiblePackages() {
        return packageRepository.findByTrialEligibleTrueAndActiveTrueAndSystemManagedFalseOrderByPriorityDesc()
                .stream()
                .map(this::toEligiblePackageResponse)
                .toList();
    }

    private PlanPackage resolveTrialPackage(Long packageId) {
        if (packageId == null) {
            throw new BadRequestException("Paket id zorunludur");
        }
        PlanPackage planPackage = packageRepository.findByIdWithItems(packageId)
                .orElseThrow(() -> new BadRequestException("Paket bulunamadi: " + packageId));
        if (!planPackage.isActive() || !planPackage.isTrialEligible() || planPackage.isSystemManaged()
                || CatalogPackages.FREE_PACKAGE.equals(planPackage.getCode())) {
            throw new BadRequestException("Bu paket deneme icin uygun degil");
        }
        if (planPackage.getItems() == null || planPackage.getItems().isEmpty()) {
            throw new BadRequestException("Deneme paketinde urun bulunamadi");
        }
        return planPackage;
    }

    private void rejectIfHasUsablePaidPackage(Long userId) {
        entitlementService.expireDuePurchasesForUser(userId);
        boolean hasPaid = purchaseRepository.findByUserIdAndStatus(userId, PurchaseStatus.ACTIVE).stream()
                .anyMatch(purchase -> purchase.isUsable()
                        && purchase.getPurchaseType() == PurchaseType.PAID);
        if (hasPaid) {
            throw new BadRequestException("Aktif ucretli paket varken deneme baslatilamaz");
        }
    }

    private TrialDtos.Status availableStatus() {
        return new TrialDtos.Status(
                TrialDtos.Lifecycle.AVAILABLE,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private TrialDtos.Status usedUnavailableStatus() {
        return new TrialDtos.Status(
                TrialDtos.Lifecycle.TRIAL_EXPIRED,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private TrialDtos.Status statusOf(Purchase purchase) {
        TrialDtos.Lifecycle lifecycle = purchase.getStatus() == PurchaseStatus.ACTIVE && !purchase.isExpiredByDate()
                ? TrialDtos.Lifecycle.ACTIVE
                : TrialDtos.Lifecycle.TRIAL_EXPIRED;

        Integer daysUntilExpiry = null;
        if (purchase.getExpiresAt() != null && lifecycle == TrialDtos.Lifecycle.ACTIVE) {
            daysUntilExpiry = (int) ChronoUnit.DAYS.between(
                    LocalDateTime.now().toLocalDate(),
                    purchase.getExpiresAt().toLocalDate()
            );
            if (daysUntilExpiry < 0) {
                daysUntilExpiry = 0;
            }
        }

        PlanPackage planPackage = purchase.getPackageId() == null
                ? null
                : packageRepository.findById(purchase.getPackageId()).orElse(null);
        BigDecimal catalogPrice = planPackage != null ? planPackage.getPrice() : null;
        String currency = planPackage != null && planPackage.getCurrency() != null
                ? planPackage.getCurrency()
                : purchase.getCurrency();

        return new TrialDtos.Status(
                lifecycle,
                purchase.getId(),
                purchase.getStartsAt(),
                purchase.getExpiresAt(),
                purchase.getPackageId(),
                purchase.getPackageCode(),
                purchase.getPackageName(),
                daysUntilExpiry,
                catalogPrice,
                currency
        );
    }

    private PlanPackageResponse toEligiblePackageResponse(PlanPackage planPackage) {
        return PlanPackageResponse.builder()
                .id(planPackage.getId())
                .code(planPackage.getCode())
                .name(planPackage.getName())
                .description(planPackage.getDescription())
                .features(planPackage.getFeatures() == null ? List.of() : List.copyOf(planPackage.getFeatures()))
                .price(planPackage.getPrice())
                .monthlyDiscount(planPackage.getMonthlyDiscount())
                .yearlyPrice(planPackage.getYearlyPrice())
                .yearlyDiscount(planPackage.getYearlyDiscount())
                .effectiveMonthlyPrice(planPackage.effectiveMonthlyPrice())
                .effectiveYearlyPrice(planPackage.effectiveYearlyPrice())
                .currency(planPackage.getCurrency())
                .active(planPackage.isActive())
                .validityDays(planPackage.getValidityDays())
                .priority(planPackage.getPriority())
                .purchasable(planPackage.isPurchasable())
                .systemManaged(planPackage.isSystemManaged())
                .trialEligible(planPackage.isTrialEligible())
                .items(planPackage.getItems() == null ? List.of() : planPackage.getItems().stream()
                        .map(item -> PlanPackageItemResponse.builder()
                                .id(item.getId())
                                .productId(item.getProduct().getId())
                                .productCode(item.getProduct().getCode())
                                .productName(item.getProduct().getName())
                                .quantity(item.getQuantity())
                                .unlimited(item.isUnlimited())
                                .build())
                        .toList())
                .createdAt(planPackage.getCreatedAt())
                .build();
    }
}
