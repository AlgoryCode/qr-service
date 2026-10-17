package com.ael.algoryqrservice.paymentservice.domain.port;

import com.ael.algoryqrservice.paymentservice.domain.model.CreatePaymentCommand;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentTransaction;

public interface PaymentNotificationPublisherPort {

    void publishPaymentSuccess(CreatePaymentCommand command, PaymentTransaction transaction);

    void publishPaymentSuccess(PaymentTransaction transaction);
}
