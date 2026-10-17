package com.ael.algoryqrservice.fulfillmentservice.infrastructure.messaging.handler;

import com.ael.algoryqrservice.fulfillmentservice.domain.model.PaymentCompletedEvent;

public interface PaymentEventHandler {

    boolean supports(String eventType);

    void handle(PaymentCompletedEvent event);
}
