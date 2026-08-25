package com.ael.algoryqrservice.service.entitlement;

/**
 * Re-derives how much of a feature a user actually occupies (live menus, QR codes, menu products)
 * and writes it back onto the fulfillment quota. Add a feature by adding an implementation.
 */
public interface FeatureUsageSynchronizer {

    /**
     * @return catalog feature code this synchronizer is responsible for.
     */
    String featureCode();

    /**
     * Recomputes and stores the used quantity for the given user.
     */
    void synchronize(Long userId);
}
