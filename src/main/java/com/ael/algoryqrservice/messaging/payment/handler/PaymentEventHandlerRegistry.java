package com.ael.algoryqrservice.messaging.payment.handler;

import com.ael.algoryqrservice.exception.InvalidPaymentEventException;
import com.ael.algoryqrservice.model.dto.PaymentCompletedEventDto;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PaymentEventHandlerRegistry {

    private final List<PaymentEventHandler> handlers;

    public PaymentEventHandlerRegistry(List<PaymentEventHandler> handlers) {
        this.handlers = List.copyOf(handlers);
    }

    public void dispatch(PaymentCompletedEventDto event) {
        String eventType = event.getEventType();
        for (PaymentEventHandler handler : handlers) {
            if (handler.supports(eventType)) {
                handler.handle(event);
                return;
            }
        }
        throw new InvalidPaymentEventException("Unsupported payment event type");
    }
}
