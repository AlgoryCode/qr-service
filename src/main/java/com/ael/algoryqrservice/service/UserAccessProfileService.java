package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.FulfillmentDetail;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.dto.UserAccessProfile;
import com.ael.algoryqrservice.repository.FulfillmentDetailRepository;
import com.ael.algoryqrservice.service.entitlement.EntitlementMaintenanceService;
import com.ael.algoryqrservice.service.entitlement.PurchaseExpiryService;
import com.ael.algoryqrservice.service.entitlement.PurchaseSelectionPolicy;
import com.ael.algoryqrservice.util.AppTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Builds the compact access snapshot (package code, feature codes, scope codes) that other
 * services and the API gateway use for coarse authorization.
 */
@Service
@RequiredArgsConstructor
public class UserAccessProfileService {

    private static final UserAccessProfile EMPTY_PROFILE = new UserAccessProfile(null, List.of(), List.of());

    private final FulfillmentDetailRepository fulfillmentDetailRepository;
    private final PurchaseExpiryService purchaseExpiryService;
    private final PurchaseSelectionPolicy purchaseSelectionPolicy;
    private final EntitlementMaintenanceService entitlementMaintenanceService;
    private final PackageActivationService packageActivationService;

    @Transactional
    public UserAccessProfile resolve(Long userId) {
        purchaseExpiryService.expireDueForUser(userId);
        packageActivationService.ensureSubscriptionState(userId);
        entitlementMaintenanceService.repairUser(userId);

        Optional<Purchase> activePurchase = purchaseSelectionPolicy
                .highestPriority(purchaseSelectionPolicy.usablePurchases(userId));
        if (activePurchase.isEmpty()) {
            return EMPTY_PROFILE;
        }

        List<FulfillmentDetail> details = fulfillmentDetailRepository.findAllActiveByUserId(userId, AppTime.nowLocal());
        return new UserAccessProfile(
                activePurchase.get().getPackageCode(),
                distinctSortedCodes(details, FulfillmentDetail::getFeatureCode),
                distinctSortedCodes(details, FulfillmentDetail::getScopeCode)
        );
    }

    private List<String> distinctSortedCodes(
            List<FulfillmentDetail> details,
            Function<FulfillmentDetail, String> codeExtractor
    ) {
        return details.stream()
                .map(codeExtractor)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }
}
