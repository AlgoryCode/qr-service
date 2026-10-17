package com.ael.algoryqrservice.fulfillmentservice.infrastructure.messaging.handler;

import com.ael.algoryqrservice.fulfillmentservice.application.service.FulfillmentCommandFactory;
import com.ael.algoryqrservice.fulfillmentservice.application.service.FulfillmentService;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.PaymentCompletedEvent;
import com.ael.algoryqrservice.qrservice.messaging.payment.PaymentEventTypes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component("fulfillmentPaymentSuccessEventHandler")
@RequiredArgsConstructor
public class PaymentSuccessEventHandler implements PaymentEventHandler {

    private static final Set<String> SUPPORTED = Set.of(
            PaymentEventTypes.PAYMENT_SUCCESS,
            PaymentEventTypes.PAYMENT_INSTALLMENT_PAID,
            PaymentEventTypes.PAYMENT_SUBSCRIPTION_PAID
    );

    private final FulfillmentService fulfillmentService;
    private final FulfillmentCommandFactory commandFactory;

    @Override
    public boolean supports(String eventType) {
        return SUPPORTED.contains(eventType);
    }

    @Override
    public void handle(PaymentCompletedEvent event) {
        fulfillmentService.fulfillPaidPackage(commandFactory.fromPaymentEvent(event));
    }
}
