package com.ael.algoryqrservice.qrservice.messaging.payment.handler;

import com.ael.algoryqrservice.qrservice.billing.dto.PaymentCompletedEventDto;

public interface PaymentEventHandler {

    boolean supports(String eventType);

    void handle(PaymentCompletedEventDto event);
}
