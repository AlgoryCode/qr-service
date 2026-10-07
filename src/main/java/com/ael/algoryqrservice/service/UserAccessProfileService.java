package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.client.FulfillmentServiceClient;
import com.ael.algoryqrservice.client.PackageView;
import com.ael.algoryqrservice.client.dto.ExternalActivePackageResponse;
import com.ael.algoryqrservice.exception.FulfillmentUnavailableException;
import com.ael.algoryqrservice.model.dto.UserAccessProfile;
import com.ael.algoryqrservice.service.entitlement.PurchaseExpiryService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserAccessProfileService {

    private static final UserAccessProfile EMPTY_PROFILE = new UserAccessProfile(null, List.of(), List.of());

    private final PurchaseExpiryService purchaseExpiryService;
    private final ObjectProvider<FulfillmentServiceClient> fulfillmentServiceClient;

    @Transactional
    public UserAccessProfile resolve(Long userId) {
        purchaseExpiryService.expireDueForUser(userId);
        FulfillmentServiceClient client = fulfillmentServiceClient.getIfAvailable();
        if (client == null) {
            throw new FulfillmentUnavailableException("Paket bilgisi şu an alınamıyor");
        }
        PackageView view = client.findPackage(userId);
        if (!"ACTIVE".equals(view.status()) || view.body() == null) {
            return EMPTY_PROFILE;
        }
        return toAccessProfile(view.body());
    }

    private UserAccessProfile toAccessProfile(ExternalActivePackageResponse response) {
        List<String> products = response.products() == null ? List.of() : response.products();
        List<String> scopes = response.scopes() == null ? List.of() : response.scopes();
        return new UserAccessProfile(response.packageCode(), products, scopes);
    }
}
