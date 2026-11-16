package com.ael.algoryqrservice.qrservice.account.dto;

import java.time.Instant;

/**
 * One thing the merchant may use, with its limit and how much of it is used.
 *
 * @param quantity  the limit; meaningless when {@code unlimited}
 * @param remaining {@code null} when unlimited
 * @param source    PACKAGE_INCLUDE, PACKAGE_MODULE or ADDON_PURCHASE
 * @param expiresAt null for credits that stay until used and for the free package
 */
public record SessionEntitlementResponse(
        String productCode,
        String featureCode,
        String scopeCode,
        Integer quantity,
        boolean unlimited,
        Integer usedQuantity,
        Integer remaining,
        String source,
        Instant expiresAt
) {
}
