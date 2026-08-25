package com.ael.algoryqrservice.service.entitlement;

import com.ael.algoryqrservice.catalog.CatalogProducts;
import com.ael.algoryqrservice.service.fulfillment.FulfillmentLedger;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.ael.algoryqrservice.model.enums.FulfillmentDetailSource.ADDON_PURCHASE;

/**
 * Extra-menu quota is occupied by every live menu beyond the first one of each branch.
 */
@Component
@RequiredArgsConstructor
public class QrMenuUsageSynchronizer implements FeatureUsageSynchronizer {

    private final ExtraMenuQuotaCalculator extraMenuQuotaCalculator;
    private final FulfillmentLedger ledger;

    @Override
    public String featureCode() {
        return CatalogProducts.QR_MENU;
    }

    @Override
    public void synchronize(Long userId) {
        int extraMenus = extraMenuQuotaCalculator.countExtraMenus(userId);
        ledger.replaceUsedQuantity(userId, featureCode(), extraMenus, List.of(ADDON_PURCHASE));
    }
}
