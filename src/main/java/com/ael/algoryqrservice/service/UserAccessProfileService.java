package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.FulfillmentDetail;
import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.dto.UserAccessProfile;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.repository.FulfillmentDetailRepository;
import com.ael.algoryqrservice.repository.PlanPackageRepository;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.util.AppTime;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class UserAccessProfileService {

    private final PurchaseRepository purchaseRepository;
    private final PlanPackageRepository planPackageRepository;
    private final FulfillmentDetailRepository fulfillmentDetailRepository;
    private final EntitlementService entitlementService;
    private final PackageActivationService packageActivationService;
    private final FulfillmentMigrationService fulfillmentMigrationService;

    public UserAccessProfileService(
            PurchaseRepository purchaseRepository,
            PlanPackageRepository planPackageRepository,
            FulfillmentDetailRepository fulfillmentDetailRepository,
            @Lazy EntitlementService entitlementService,
            @Lazy PackageActivationService packageActivationService,
            @Lazy FulfillmentMigrationService fulfillmentMigrationService
    ) {
        this.purchaseRepository = purchaseRepository;
        this.planPackageRepository = planPackageRepository;
        this.fulfillmentDetailRepository = fulfillmentDetailRepository;
        this.entitlementService = entitlementService;
        this.packageActivationService = packageActivationService;
        this.fulfillmentMigrationService = fulfillmentMigrationService;
    }

    @Transactional
    public UserAccessProfile resolve(Long userId) {
        entitlementService.expireDuePurchasesForUser(userId);
        packageActivationService.ensureSubscriptionState(userId);
        entitlementService.repairUsablePackageEntitlements(userId);
        fulfillmentMigrationService.backfillUser(userId);

        List<Purchase> usablePurchases = purchaseRepository.findByUserIdAndStatus(userId, PurchaseStatus.ACTIVE).stream()
                .filter(Purchase::isUsable)
                .toList();

        Map<Long, Integer> priorityByPackageId = planPackageRepository.findAllById(
                usablePurchases.stream().map(Purchase::getPackageId).distinct().toList()
        ).stream().collect(Collectors.toMap(
                PlanPackage::getId,
                planPackage -> planPackage.getPriority() == null ? 0 : planPackage.getPriority()
        ));

        Purchase activePurchase = usablePurchases.stream()
                .max(Comparator.comparingInt(purchase ->
                        priorityByPackageId.getOrDefault(purchase.getPackageId(), 0)))
                .orElse(null);

        if (activePurchase == null) {
            return new UserAccessProfile(null, List.of(), List.of());
        }

        List<FulfillmentDetail> details = fulfillmentDetailRepository.findAllActiveByUserId(userId, AppTime.nowLocal());
        List<String> products = details.stream()
                .map(FulfillmentDetail::getFeatureCode)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        List<String> scopes = details.stream()
                .map(FulfillmentDetail::getScopeCode)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();

        return new UserAccessProfile(activePurchase.getPackageCode(), products, scopes);
    }
}
