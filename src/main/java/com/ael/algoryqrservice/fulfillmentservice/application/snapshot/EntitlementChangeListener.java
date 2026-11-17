package com.ael.algoryqrservice.fulfillmentservice.application.snapshot;

/**
 * Told after a merchant's entitlements changed and the change is committed (purchase, renewal,
 * downgrade, expiry, an admin grant). Runs outside the writer's transaction; a failure is logged
 * and never undoes the change.
 */
public interface EntitlementChangeListener {

    void entitlementsChanged(Long merchantId);
}
