package com.ael.algoryqrservice.service.entitlement;

import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.repository.PlanPackageRepository;
import com.ael.algoryqrservice.service.FulfillmentGrantService;
import com.ael.algoryqrservice.service.FulfillmentMigrationService;
import com.ael.algoryqrservice.util.WritableTransactionGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Self-healing pass over a user's entitlements: re-derives package rights, repairs add-ons,
 * backfills fulfillment rows and re-counts usage.
 *
 * <p>Every step writes and takes pessimistic locks, so the whole pass is skipped when it is
 * reached from a read-only transaction.
 */
@Service
@RequiredArgsConstructor
public class EntitlementMaintenanceService {

    private final PurchaseSelectionPolicy selectionPolicy;
    private final PlanPackageRepository planPackageRepository;
    private final PackageEntitlementWriter entitlementWriter;
    private final FulfillmentGrantService fulfillmentGrantService;
    private final FulfillmentMigrationService fulfillmentMigrationService;
    private final FeatureUsageSyncRegistry usageSyncRegistry;
    private final WritableTransactionGuard writableTransactionGuard;

    @Transactional
    public void repairUser(Long userId) {
        if (userId == null || !writableTransactionGuard.allowsWrites("entitlement repair", userId)) {
            return;
        }
        repairPackageEntitlements(userId);
        repairAddonEntitlements(userId);
        fulfillmentMigrationService.backfillUser(userId);
        usageSyncRegistry.synchronizeAll(userId);
    }

    @Transactional
    public void backfillFulfillment(Long userId) {
        if (userId == null || !writableTransactionGuard.allowsWrites("fulfillment backfill", userId)) {
            return;
        }
        fulfillmentMigrationService.backfillUser(userId);
    }

    private void repairPackageEntitlements(Long userId) {
        List<Purchase> subscriptions = selectionPolicy.usableSubscriptions(userId).stream()
                .filter(purchase -> purchase.getPackageId() != null)
                .toList();
        if (subscriptions.isEmpty()) {
            return;
        }
        Map<Long, PlanPackage> packagesById = loadPackages(subscriptions);
        for (Purchase purchase : subscriptions) {
            PlanPackage planPackage = packagesById.get(purchase.getPackageId());
            if (planPackage != null) {
                entitlementWriter.ensureEntitlementsForPackage(purchase, planPackage);
            }
        }
    }

    private void repairAddonEntitlements(Long userId) {
        fulfillmentGrantService.repairAddonFulfillmentsForUser(userId);
        selectionPolicy.usableAddons(userId).forEach(entitlementWriter::repairAddonEntitlements);
    }

    private Map<Long, PlanPackage> loadPackages(List<Purchase> purchases) {
        List<Long> packageIds = purchases.stream()
                .map(Purchase::getPackageId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return planPackageRepository.findAllByIdWithItems(packageIds).stream()
                .collect(Collectors.toMap(PlanPackage::getId, Function.identity(), (left, right) -> left));
    }
}
