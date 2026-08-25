package com.ael.algoryqrservice.service.entitlement;

import com.ael.algoryqrservice.catalog.CatalogProducts;
import com.ael.algoryqrservice.repository.QrRepository;
import com.ael.algoryqrservice.service.fulfillment.FulfillmentLedger;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.ael.algoryqrservice.model.enums.FulfillmentDetailSource.ADDON_PURCHASE;
import static com.ael.algoryqrservice.model.enums.FulfillmentDetailSource.PACKAGE_INCLUDE;

/**
 * QR creation quota is occupied by every non-deleted QR code the user owns.
 */
@Component
@RequiredArgsConstructor
public class QrCreateUsageSynchronizer implements FeatureUsageSynchronizer {

    private final QrRepository qrRepository;
    private final FulfillmentLedger ledger;

    @Override
    public String featureCode() {
        return CatalogProducts.QR_CREATE;
    }

    @Override
    public void synchronize(Long userId) {
        long activeQrCount = qrRepository.countByUserIdAndDeletedFalse(userId);
        ledger.replaceUsedQuantity(
                userId,
                featureCode(),
                (int) Math.min(activeQrCount, Integer.MAX_VALUE),
                List.of(PACKAGE_INCLUDE, ADDON_PURCHASE)
        );
    }
}
