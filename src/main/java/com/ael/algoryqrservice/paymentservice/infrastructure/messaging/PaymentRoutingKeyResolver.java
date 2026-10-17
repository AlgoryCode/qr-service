package com.ael.algoryqrservice.paymentservice.infrastructure.messaging;

import org.springframework.stereotype.Component;

@Component
public class PaymentRoutingKeyResolver {

    public String resolve(String serviceName) {
        return serviceName + ".payment.events";
    }

    public String resolve(String serviceName, boolean success) {
        return resolve(serviceName);
    }
}
