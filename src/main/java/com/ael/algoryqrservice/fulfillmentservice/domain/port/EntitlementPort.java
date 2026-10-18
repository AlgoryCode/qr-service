package com.ael.algoryqrservice.fulfillmentservice.domain.port;

import com.ael.algoryqrservice.fulfillmentservice.domain.model.UserEntitlement;

import java.util.List;

public interface EntitlementPort {

    List<UserEntitlement> saveAll(List<UserEntitlement> entitlements);

    UserEntitlement save(UserEntitlement entitlement);

    List<UserEntitlement> findActiveByMerchantId(Long merchantId);

    void supersedeOtherEntitlements(Long merchantId, Long keepFulfillmentId);
}
