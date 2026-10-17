package com.ael.algoryqrservice.paymentservice.domain.port;

import com.ael.algoryqrservice.paymentservice.domain.model.PaymentCompletedEvent;

public interface PaymentEventPublisherPort {

    void publish(PaymentCompletedEvent event, String serviceName, boolean success);
}
