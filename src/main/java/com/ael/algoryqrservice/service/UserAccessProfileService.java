package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.access.AccessSession;
import com.ael.algoryqrservice.access.SessionAccessService;
import com.ael.algoryqrservice.model.FulfillmentDetail;
import com.ael.algoryqrservice.model.dto.UserAccessProfile;
import com.ael.algoryqrservice.repository.FulfillmentDetailRepository;
import com.ael.algoryqrservice.service.entitlement.EntitlementMaintenanceService;
import com.ael.algoryqrservice.service.entitlement.PurchaseExpiryService;
import com.ael.algoryqrservice.util.AppTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class UserAccessProfileService {

    private static final UserAccessProfile EMPTY_PROFILE = new UserAccessProfile(null, List.of(), List.of());

    private final FulfillmentDetailRepository fulfillmentDetailRepository;
    private final PurchaseExpiryService purchaseExpiryService;
    private final EntitlementMaintenanceService entitlementMaintenanceService;
    private final PackageActivationService packageActivationService;
    private final SessionAccessService sessionAccessService;

    @Transactional
    public UserAccessProfile resolve(Long userId) {
        purchaseExpiryService.expireDueForUser(userId);
        packageActivationService.ensureSubscriptionState(userId);
        entitlementMaintenanceService.repairUser(userId);

        AccessSession session = sessionAccessService.resolve(userId);
        if (!session.isAllow()) {
            return EMPTY_PROFILE;
        }

        List<FulfillmentDetail> details = fulfillmentDetailRepository.findAllActiveByUserId(userId, AppTime.nowLocal());
        return new UserAccessProfile(
                session.packageCode(),
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
