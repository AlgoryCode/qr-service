package com.ael.algoryqrservice.service.fulfillment;

import com.ael.algoryqrservice.model.FulfillmentDetail;
import com.ael.algoryqrservice.model.enums.FulfillmentUsageAction;

/**
 * How a single {@link FulfillmentDetail} row moves when quota is taken or given back.
 * Adding a new bookkeeping operation means adding an implementation, not editing the ledger.
 */
public interface FulfillmentDetailAdjustment {

    /** Usage-log action recorded for this adjustment. */
    FulfillmentUsageAction action();

    /** How much of the requested amount this detail can absorb. */
    int capacity(FulfillmentDetail detail);

    /** Applies the (already capped) amount to the detail. */
    void apply(FulfillmentDetail detail, int amount);
}
