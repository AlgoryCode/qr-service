package com.ael.algoryqrservice.service.fulfillment;

import com.ael.algoryqrservice.model.enums.FulfillmentReferenceType;

/**
 * What a quota movement was booked against, e.g. the menu or QR that triggered it.
 */
public record UsageReference(FulfillmentReferenceType type, Long id) {

    public static UsageReference of(FulfillmentReferenceType type, Long id) {
        return new UsageReference(type, id);
    }
}
