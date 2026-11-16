package com.ael.algoryqrservice.qrservice.access;

import com.ael.algoryqrservice.qrservice.billing.externalpackageresponsemapper.service.ExternalPackageResponseMapper;
import com.ael.algoryqrservice.qrservice.client.FulfillmentServiceClient;
import com.ael.algoryqrservice.qrservice.client.PackageView;
import com.ael.algoryqrservice.qrservice.model.enums.AccessDecision;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * The one place that decides whether a merchant may use the panel.
 *
 * <ul>
 *   <li>ALLOW: any open package (trial, paid, free, or paid in payment grace); {@code billingState}
 *       tells the panel which banner to show.</li>
 *   <li>START_PACKAGE: the merchant never had a package; the panel offers the trial.</li>
 *   <li>REQUIRE_PURCHASE: the merchant had a package that is closed. Only happens when the free
 *       package is unavailable, since every ending package normally falls back to free.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class SessionAccessService {

    private final FulfillmentServiceClient fulfillmentServiceClient;
    private final ExternalPackageResponseMapper externalPackageResponseMapper;

    public AccessSession resolve(Long merchantId) {
        if (merchantId == null) {
            return AccessSession.of(AccessDecision.REQUIRE_PURCHASE, null, null, null);
        }
        PackageView view = fulfillmentServiceClient.findPackage(merchantId);
        if (view.body() != null && view.body().isOpen()) {
            return externalPackageResponseMapper.toAllowSession(view.body());
        }
        return closed(merchantId);
    }

    /** Decision for a merchant without an open package. */
    public AccessSession closed(Long merchantId) {
        if (merchantId != null && !fulfillmentServiceClient.hasAnyPackage(merchantId)) {
            return AccessSession.of(AccessDecision.START_PACKAGE, null, null, null);
        }
        return AccessSession.of(AccessDecision.REQUIRE_PURCHASE, null, null, null);
    }
}
