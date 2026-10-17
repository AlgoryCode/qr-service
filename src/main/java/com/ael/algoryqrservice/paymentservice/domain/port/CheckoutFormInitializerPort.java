package com.ael.algoryqrservice.paymentservice.domain.port;

import com.ael.algoryqrservice.paymentservice.domain.model.CheckoutFormKind;
import com.ael.algoryqrservice.paymentservice.domain.model.InitializeCheckoutFormCommand;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentGatewayResult;

public interface CheckoutFormInitializerPort {

    CheckoutFormKind kind();

    PaymentGatewayResult initialize(InitializeCheckoutFormCommand command);
}
