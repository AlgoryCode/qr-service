package com.ael.algoryqrservice.fulfillmentservice.domain.model;

import java.time.Instant;
import java.util.List;

/** A paid add-on purchase turned into spendable entitlements, one per purchased product line. */
public record AddonGrant(Long merchantId, Long purchaseId, List<Line> lines) {

    public AddonGrant {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }

    /**
     * @param expiresAt {@code null} for consumable credits (e.g. extra smart reports) that stay until used
     */
    public record Line(Long productId, String featureCode, String scopeCode, int quantity, Instant expiresAt) {
    }
}
