package com.ael.algoryqrservice.trial.extend;

import com.ael.algoryqrservice.catalog.CatalogPackages;
import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.PlanPackageItem;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.enums.BillingPeriod;
import com.ael.algoryqrservice.model.enums.PaymentStyle;
import com.ael.algoryqrservice.model.enums.PurchaseLogAction;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.repository.PlanPackageRepository;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.service.PurchaseLogService;
import com.ael.algoryqrservice.service.entitlement.PackageEntitlementWriter;
import com.ael.algoryqrservice.trial.domain.TrialLifecycle;
import com.ael.algoryqrservice.trial.domain.TrialSnapshot;
import com.ael.algoryqrservice.util.AppTime;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class NeverStartedTrialExtendHandler implements TrialExtendHandler {

    private final PurchaseRepository purchaseRepository;
    private final PlanPackageRepository packageRepository;
    private final PackageEntitlementWriter entitlementWriter;
    private final PurchaseLogService purchaseLogService;
    private final TrialPurchaseExtender trialPurchaseExtender;

    @Override
    public TrialLifecycle supports() {
        return TrialLifecycle.NEVER_STARTED;
    }

    @Override
    public Purchase extend(Long userId, TrialSnapshot snapshot, int days) {
        PlanPackage ultimate = packageRepository.findByCode(CatalogPackages.ULTIMATE_PACKAGE)
                .flatMap(existing -> packageRepository.findByIdWithItems(existing.getId()))
                .filter(planPackage -> planPackage.isActive() && !planPackage.isSystemManaged())
                .orElseThrow(() -> new BadRequestException("Ultimate paketi bulunamadi veya aktif degil"));

        LocalDateTime startsAt = AppTime.nowLocal();
        try {
            Purchase purchase = purchaseRepository.saveAndFlush(Purchase.builder()
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

            for (PlanPackageItem item : ultimate.getItems()) {
                entitlementWriter.grant(
                        purchase,
                        item.getProduct().getId(),
                        item.getProduct().getCode(),
                        item.getQuantity(),
                        item.isUnlimited()
                );
            }

            purchaseLogService.log(
                    purchase.getId(),
                    userId,
                    PurchaseLogAction.TRIAL_STARTED,
                    "Admin deneme baslatildi. Bitis: " + purchase.getExpiresAt()
            );
            return purchase;
        } catch (DataIntegrityViolationException exception) {
            Purchase existing = purchaseRepository
                    .findFirstByUserIdAndPurchaseTypeOrderByPurchasedAtDesc(userId, PurchaseType.TRIAL)
                    .orElseThrow(() -> new BadRequestException("Deneme kaydi olusturulamadi"));
            return trialPurchaseExtender.addDays(existing, days, PurchaseLogAction.TRIAL_EXTENDED);
        }
    }
}
