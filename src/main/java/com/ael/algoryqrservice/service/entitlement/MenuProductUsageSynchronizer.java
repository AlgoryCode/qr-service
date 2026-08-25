package com.ael.algoryqrservice.service.entitlement;

import com.ael.algoryqrservice.catalog.CatalogProducts;
import com.ael.algoryqrservice.repository.MenuProductRepository;
import com.ael.algoryqrservice.service.fulfillment.FulfillmentLedger;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.ael.algoryqrservice.model.enums.FulfillmentDetailSource.ADDON_PURCHASE;
import static com.ael.algoryqrservice.model.enums.FulfillmentDetailSource.PACKAGE_INCLUDE;

/**
 * Menu-product quota is occupied by every active product across the user's menus.
 */
@Component
@RequiredArgsConstructor
public class MenuProductUsageSynchronizer implements FeatureUsageSynchronizer {

    private final MenuProductRepository menuProductRepository;
    private final FulfillmentLedger ledger;

    @Override
    public String featureCode() {
        return CatalogProducts.MENU_PRODUCT;
    }

    @Override
    public void synchronize(Long userId) {
        long activeProducts = menuProductRepository.countActiveProductsForUser(userId);
        ledger.replaceUsedQuantity(
                userId,
                featureCode(),
                (int) Math.min(activeProducts, Integer.MAX_VALUE),
                List.of(PACKAGE_INCLUDE, ADDON_PURCHASE)
        );
    }
}
