package com.ael.algoryqrservice.qrservice.account.service;

import com.ael.algoryqrservice.qrservice.client.FulfillmentServiceClient;
import com.ael.algoryqrservice.qrservice.client.PackageView;
import com.ael.algoryqrservice.qrservice.client.dto.ExternalActivePackageResponse;
import com.ael.algoryqrservice.qrservice.account.dto.UserAccessProfile;
import com.ael.algoryqrservice.qrservice.billing.service.entitlement.PurchaseExpiryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserAccessProfileService {

    private static final UserAccessProfile EMPTY_PROFILE = new UserAccessProfile(null, List.of(), List.of());

    private final PurchaseExpiryService purchaseExpiryService;
    private final FulfillmentServiceClient fulfillmentServiceClient;

    @Transactional
    public UserAccessProfile resolve(Long userId) {
        purchaseExpiryService.expireDueForUser(userId);
        PackageView view = fulfillmentServiceClient.findPackage(userId);
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
