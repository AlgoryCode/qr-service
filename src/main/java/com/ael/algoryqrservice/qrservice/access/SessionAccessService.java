package com.ael.algoryqrservice.qrservice.access;

import com.ael.algoryqrservice.qrservice.client.FulfillmentServiceClient;
import com.ael.algoryqrservice.qrservice.client.PackageView;
import com.ael.algoryqrservice.qrservice.model.enums.AccessDecision;
import com.ael.algoryqrservice.qrservice.billing.externalpackageresponsemapper.service.ExternalPackageResponseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SessionAccessService {

    private final FulfillmentServiceClient fulfillmentServiceClient;
    private final ExternalPackageResponseMapper externalPackageResponseMapper;

    public AccessSession resolve(Long merchantId) {
        if (merchantId == null) {
            return requirePurchase();
        }
        PackageView view = fulfillmentServiceClient.findPackage(merchantId);
        if (!"ACTIVE".equals(view.status()) || view.body() == null) {
            return requirePurchase();
        }
        return externalPackageResponseMapper.toOpenSession(view.body()).orElseGet(this::requirePurchase);
    }

    private AccessSession requirePurchase() {
        return AccessSession.of(AccessDecision.REQUIRE_PURCHASE, null, null, null);
    }
}
