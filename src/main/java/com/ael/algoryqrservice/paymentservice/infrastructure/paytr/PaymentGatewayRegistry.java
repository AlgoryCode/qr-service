package com.ael.algoryqrservice.paymentservice.infrastructure.paytr;

import com.ael.algoryqrservice.paymentservice.domain.exception.PaymentProcessingException;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentProvider;
import com.ael.algoryqrservice.paymentservice.domain.port.PaymentGatewayPort;
import com.ael.algoryqrservice.paymentservice.infrastructure.config.PaymentGatewayProperties;
import com.ael.algoryqrservice.paymentservice.infrastructure.paytr.adapter.PaytrPaymentGatewayAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentGatewayRegistry {

    private final PaytrPaymentGatewayAdapter paytrPaymentGateway;
    private final PaymentGatewayProperties paymentGatewayProperties;

    public PaymentGatewayPort resolve(PaymentProvider provider) {
        PaymentProvider effective = provider == null
                ? paymentGatewayProperties.defaultProvider()
                : provider;
        if (effective != PaymentProvider.PAYTR) {
            throw new PaymentProcessingException(
                    "UNSUPPORTED_PROVIDER",
                    "Only PayTR is supported"
            );
        }
        return paytrPaymentGateway;
    }

    public PaymentProvider defaultProvider() {
        return paymentGatewayProperties.defaultProvider();
    }
}
