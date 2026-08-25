package com.ael.algoryqrservice.service.fulfillment;

import com.ael.algoryqrservice.config.AppProperties;
import com.ael.algoryqrservice.model.enums.FulfillmentGateMode;
import com.ael.algoryqrservice.repository.GrantFulfillmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Single place that maps {@link FulfillmentGateMode} to a {@link FulfillmentQuotaStrategy}.
 * In {@code DUAL} mode the choice is per user: anybody who already owns a grant is served from
 * fulfillment details, everybody else still falls back to legacy entitlements.
 */
@Component
@RequiredArgsConstructor
public class FulfillmentQuotaStrategyFactory {

    private final FulfillmentDetailQuotaStrategy detailStrategy;
    private final LegacyEntitlementQuotaStrategy legacyStrategy;
    private final GrantFulfillmentRepository grantFulfillmentRepository;
    private final AppProperties appProperties;

    public FulfillmentQuotaStrategy strategyFor(Long userId) {
        return switch (appProperties.getFulfillment().getGateMode()) {
            case ENTITLEMENT_ONLY -> legacyStrategy;
            case FULFILLMENT_ONLY -> detailStrategy;
            case DUAL -> hasMigratedGrants(userId) ? detailStrategy : legacyStrategy;
        };
    }

    private boolean hasMigratedGrants(Long userId) {
        return userId != null && grantFulfillmentRepository.existsByUserId(userId);
    }
}
