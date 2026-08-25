package com.ael.algoryqrservice.service.fulfillment;

import com.ael.algoryqrservice.model.FulfillmentDetail;
import com.ael.algoryqrservice.model.enums.FulfillmentUsageAction;
import org.springframework.stereotype.Component;

/**
 * Takes quota out of a detail row.
 */
@Component
public class ConsumeAdjustment implements FulfillmentDetailAdjustment {

    @Override
    public FulfillmentUsageAction action() {
        return FulfillmentUsageAction.CONSUME;
    }

    @Override
    public int capacity(FulfillmentDetail detail) {
        return detail.remainingQuantity();
    }

    @Override
    public void apply(FulfillmentDetail detail, int amount) {
        detail.setUsedQuantity(detail.getUsedQuantity() + amount);
    }
}
