package com.ael.algoryqrservice.service.fulfillment;

import com.ael.algoryqrservice.repository.FulfillmentDetailRepository;
import com.ael.algoryqrservice.util.AppTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Reads quotas from {@code tbl_fulfillment_detail}, the current source of truth.
 */
@Component
@RequiredArgsConstructor
public class FulfillmentDetailQuotaStrategy implements FulfillmentQuotaStrategy {

    private final FulfillmentDetailRepository fulfillmentDetailRepository;

    @Override
    public boolean hasScope(Long userId, String scopeCode) {
        return fulfillmentDetailRepository.existsActiveByScopeCode(userId, scopeCode, AppTime.nowLocal());
    }

    @Override
    public int sumAddonQuantity(Long userId, String featureCode) {
        return fulfillmentDetailRepository.sumActiveAddonQuantityByFeatureCode(userId, featureCode, AppTime.nowLocal());
    }

    @Override
    public boolean supportsLedger() {
        return true;
    }
}
