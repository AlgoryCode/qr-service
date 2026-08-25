package com.ael.algoryqrservice.service.fulfillment;

/**
 * Read side of the entitlement gate. One implementation per storage generation
 * (legacy {@code UserEntitlement} rows and current {@code FulfillmentDetail} rows) so that
 * callers never branch on {@link com.ael.algoryqrservice.model.enums.FulfillmentGateMode}.
 */
public interface FulfillmentQuotaStrategy {

    /**
     * @return {@code true} when the user currently owns the given scope.
     */
    boolean hasScope(Long userId, String scopeCode);

    /**
     * @return total quantity granted by add-on purchases for the feature.
     */
    int sumAddonQuantity(Long userId, String featureCode);

    /**
     * @return {@code true} when this storage generation also supports consume/release bookkeeping.
     */
    boolean supportsLedger();
}
