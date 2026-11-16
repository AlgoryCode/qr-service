package com.ael.algoryqrservice.fulfillmentservice.domain.port;

import com.ael.algoryqrservice.fulfillmentservice.domain.model.EntitlementSource;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.PackageStatus;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.UserEntitlement;

import java.util.List;

public interface EntitlementPort {

    List<UserEntitlement> saveAll(List<UserEntitlement> entitlements);

    UserEntitlement save(UserEntitlement entitlement);

    List<UserEntitlement> findActiveByMerchantId(Long merchantId);

    /**
     * Same rows as {@link #findActiveByMerchantId} but row-locked until the surrounding transaction
     * ends, so two concurrent usage changes cannot both read the old counter.
     */
    List<UserEntitlement> lockActiveByMerchantId(Long merchantId);

    /** Moves expiry of the purchase's active add-on rows to {@code expiresAt} (recurring add-on renewed). */
    int extendAddonExpiry(Long purchaseId, java.time.Instant expiresAt);

    void supersedeOtherEntitlements(Long merchantId, Long keepFulfillmentId);

    boolean existsByPurchaseIdAndSource(Long purchaseId, EntitlementSource source);

    /** Moves the purchase's active entitlements of {@code source} to {@code newStatus}; returns how many. */
    int changeStatusByPurchaseId(Long purchaseId, EntitlementSource source, PackageStatus newStatus);
}
