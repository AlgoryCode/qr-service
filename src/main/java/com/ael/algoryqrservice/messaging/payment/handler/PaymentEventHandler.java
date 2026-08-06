package com.ael.algoryqrservice.messaging.payment.handler;

import com.ael.algoryqrservice.model.dto.PaymentCompletedEventDto;

public interface PaymentEventHandler {

    boolean supports(String eventType);

    void handle(PaymentCompletedEventDto event);
}
