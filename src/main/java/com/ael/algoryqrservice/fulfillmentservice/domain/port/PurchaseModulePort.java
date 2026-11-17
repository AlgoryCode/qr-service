package com.ael.algoryqrservice.fulfillmentservice.domain.port;

import com.ael.algoryqrservice.fulfillmentservice.domain.model.AddonGrant;

import java.util.List;

/** Module lines the buyer added on top of a package at checkout (purchase_items). */
public interface PurchaseModulePort {

    /** One line per purchased module product; {@code expiresAt} is left null for the caller to set. */
    List<AddonGrant.Line> modulesOf(Long purchaseId);
}
