package com.ael.algoryqrservice.fulfillmentservice.domain.port;

import com.ael.algoryqrservice.fulfillmentservice.domain.model.PlanPackageItem;

import java.util.List;
import java.util.Optional;

/** The package lines a purchase was bought on; empty when the purchase is not pinned to a version. */
public interface PlanVersionPort {

    Optional<List<PlanPackageItem>> linesOfPurchase(Long purchaseId);
}
