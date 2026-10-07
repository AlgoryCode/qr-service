package com.ael.algoryqrservice.access;

import com.ael.algoryqrservice.client.FulfillmentServiceClient;
import com.ael.algoryqrservice.client.PackageView;
import com.ael.algoryqrservice.exception.FulfillmentUnavailableException;
import com.ael.algoryqrservice.model.enums.AccessDecision;
import com.ael.algoryqrservice.service.ExternalPackageResponseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SessionAccessService {

    private final ObjectProvider<FulfillmentServiceClient> fulfillmentServiceClient;
    private final ExternalPackageResponseMapper externalPackageResponseMapper;

    public AccessSession resolve(Long userId) {
        if (userId == null) {
            return requirePurchase();
        }
        PackageView view = client().findPackage(userId);
        if (!"ACTIVE".equals(view.status()) || view.body() == null) {
            return requirePurchase();
        }
        return externalPackageResponseMapper.toOpenSession(view.body()).orElseGet(this::requirePurchase);
    }

    private FulfillmentServiceClient client() {
        FulfillmentServiceClient client = fulfillmentServiceClient.getIfAvailable();
        if (client == null) {
            throw new FulfillmentUnavailableException("Paket bilgisi şu an alınamıyor");
        }
        return client;
    }

    private AccessSession requirePurchase() {
        return AccessSession.of(AccessDecision.REQUIRE_PURCHASE, null, null, null);
    }
}
