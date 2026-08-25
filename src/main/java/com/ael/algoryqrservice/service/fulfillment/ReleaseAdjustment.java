package com.ael.algoryqrservice.service.fulfillment;

import com.ael.algoryqrservice.model.FulfillmentDetail;
import com.ael.algoryqrservice.model.enums.FulfillmentUsageAction;
import org.springframework.stereotype.Component;

/**
 * Gives quota back to a detail row.
 */
@Component
public class ReleaseAdjustment implements FulfillmentDetailAdjustment {

    @Override
    public FulfillmentUsageAction action() {
        return FulfillmentUsageAction.RELEASE;
    }

    @Override
    public int capacity(FulfillmentDetail detail) {
        return detail.getUsedQuantity();
    }

    @Override
    public void apply(FulfillmentDetail detail, int amount) {
        detail.setUsedQuantity(detail.getUsedQuantity() - amount);
    }
}
