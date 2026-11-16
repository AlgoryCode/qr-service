package com.ael.algoryqrservice.fulfillmentservice.domain.port;

import com.ael.algoryqrservice.fulfillmentservice.domain.model.SubscriptionEvent;

import java.util.List;

/** Append-only audit trail of subscription changes ({@code subscription_history}). */
public interface SubscriptionHistoryPort {

    /** Stores the event; returns false when an event with the same dedupe key already exists. */
    boolean record(SubscriptionEvent event);

    List<SubscriptionEvent> findByMerchant(Long merchantId, int limit);
}
