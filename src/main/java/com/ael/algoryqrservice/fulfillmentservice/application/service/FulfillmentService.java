package com.ael.algoryqrservice.fulfillmentservice.application.service;

import com.ael.algoryqrservice.fulfillmentservice.domain.model.ActivePackage;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.ActivePackageView;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.FulfillmentCommand;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.PackageFulfillment;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.AddonGrant;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.UserEntitlement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface FulfillmentService {

    PackageFulfillment fulfillPaidPackage(FulfillmentCommand command);

    void assignPackage(Long merchantId, String packageCode, int periodDays);

    Optional<ActivePackage> findActivePackage(Long merchantId);

    /** The merchant's package row whatever its status (closed ones included). */
    Optional<ActivePackage> findCurrentPackage(Long merchantId);

    boolean hasAnyPackage(Long merchantId);

    ActivePackage requireActivePackage(Long merchantId);

    ActivePackageView requireActivePackageView(Long merchantId);

    PackageFulfillment requireFulfillment(Long fulfillmentId);

    Page<PackageFulfillment> listFulfillments(Long merchantId, Pageable pageable);

    List<UserEntitlement> listActiveEntitlements(Long merchantId);

    /** Writes the add-on's entitlements once per purchase; replays are ignored. */
    List<UserEntitlement> grantAddon(AddonGrant grant);

    /** Cancels a refunded or cancelled add-on's remaining entitlements. */
    int revokeAddon(Long purchaseId);

    /** Moves an add-on's active entitlements to a new end date (recurring modules renewing with the package). */
    int extendAddon(Long purchaseId, java.time.Instant expiresAt);
}
