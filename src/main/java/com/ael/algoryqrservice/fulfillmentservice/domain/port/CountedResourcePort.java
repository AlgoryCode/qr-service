package com.ael.algoryqrservice.fulfillmentservice.domain.port;

import java.util.OptionalLong;

/**
 * Live count of what a merchant holds for a capacity feature (branches, menus, menu products, QR
 * codes). Usage of these features is never stored as a counter: it is whatever exists right now, so
 * a failed create or a delete can never leave the limit out of step.
 */
public interface CountedResourcePort {

    /** Empty when {@code featureCode} is not a counted capacity feature. */
    OptionalLong count(Long merchantId, String featureCode);

    boolean isCounted(String featureCode);
}
