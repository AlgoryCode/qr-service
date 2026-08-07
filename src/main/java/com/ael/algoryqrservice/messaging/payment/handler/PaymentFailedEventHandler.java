package com.ael.algoryqrservice.messaging.payment.handler;

import com.ael.algoryqrservice.messaging.payment.PaymentEventTypes;
import com.ael.algoryqrservice.model.dto.PaymentCompletedEventDto;
import com.ael.algoryqrservice.service.PurchaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class PaymentFailedEventHandler implements PaymentEventHandler {

    private static final Set<String> SUPPORTED = Set.of(
            PaymentEventTypes.PAYMENT_FAILED,
            PaymentEventTypes.PAYMENT_INSTALLMENT_FAILED,
            PaymentEventTypes.PAYMENT_SUBSCRIPTION_FAILED,
            PaymentEventTypes.PAYMENT_SUBSCRIPTION_PAST_DUE
    );

    private final PurchaseService purchaseService;

    @Override
    public boolean supports(String eventType) {
        return SUPPORTED.contains(eventType);
    }

    @Override
    public void handle(PaymentCompletedEventDto event) {
        if (PaymentEventTypes.PAYMENT_SUBSCRIPTION_PAST_DUE.equals(event.getEventType())) {
            purchaseService.handleSubscriptionPastDue(event);
            return;
        }
        purchaseService.handlePaymentFailed(event);
    }
}
