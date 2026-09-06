package com.ael.algoryqrservice.trial.extend;

import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.enums.PurchaseLogAction;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.repository.PlanPackageRepository;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.service.PurchaseLogService;
import com.ael.algoryqrservice.service.entitlement.PackageEntitlementWriter;
import com.ael.algoryqrservice.util.AppTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class TrialPurchaseExtender {

    private final PurchaseRepository purchaseRepository;
    private final PlanPackageRepository packageRepository;
    private final PackageEntitlementWriter entitlementWriter;
    private final PurchaseLogService purchaseLogService;

    public Purchase addDays(Purchase trial, int days, PurchaseLogAction action) {
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

        purchaseLogService.log(
                trial.getId(),
                trial.getUserId(),
                action,
                "Admin deneme suresi " + days + " gun eklendi. Yeni bitis: " + trial.getExpiresAt()
        );
        return trial;
    }
}
