package com.ael.algoryqrservice.fulfillmentservice.infrastructure.messaging.handler;

import com.ael.algoryqrservice.fulfillmentservice.domain.exception.InvalidPaymentEventException;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.PaymentCompletedEvent;
import org.springframework.stereotype.Component;

import java.util.List;

@Component("fulfillmentPaymentEventHandlerRegistry")
public class PaymentEventHandlerRegistry {

    private final List<PaymentEventHandler> handlers;

    public PaymentEventHandlerRegistry(List<PaymentEventHandler> handlers) {
        this.handlers = List.copyOf(handlers);
    }

    public void dispatch(PaymentCompletedEvent event) {
        String eventType = event.eventType();
        for (PaymentEventHandler handler : handlers) {
            if (handler.supports(eventType)) {
                handler.handle(event);
                return;
            }
        }
        throw new InvalidPaymentEventException("Unsupported payment event type: " + eventType);
    }
}
