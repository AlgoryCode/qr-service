package com.ael.algoryqrservice.paymentservice.infrastructure.paytr.checkout;

import com.ael.algoryqrservice.paymentservice.domain.exception.PaymentProcessingException;
import com.ael.algoryqrservice.paymentservice.domain.model.CheckoutFormKind;
import com.ael.algoryqrservice.paymentservice.domain.model.InitializeCheckoutFormCommand;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentGatewayResult;
import com.ael.algoryqrservice.paymentservice.domain.port.CheckoutFormInitializerPort;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class CheckoutFormInitializerFactory {

    private final Map<CheckoutFormKind, CheckoutFormInitializerPort> initializers;

    public CheckoutFormInitializerFactory(List<CheckoutFormInitializerPort> initializerPorts) {
        Map<CheckoutFormKind, CheckoutFormInitializerPort> mapped = new EnumMap<>(CheckoutFormKind.class);
        for (CheckoutFormInitializerPort initializer : initializerPorts) {
            mapped.put(initializer.kind(), initializer);
        }
        this.initializers = Map.copyOf(mapped);
    }

    public PaymentGatewayResult initialize(InitializeCheckoutFormCommand command) {
        CheckoutFormInitializerPort initializer = initializers.get(CheckoutFormKind.IFRAME);
        if (initializer == null) {
            throw new PaymentProcessingException(
                    "PAYTR_CHECKOUT_INITIALIZER_MISSING",
                    "PayTR checkout initializer bulunamadi: IFRAME"
            );
        }
        return initializer.initialize(command);
    }
}
