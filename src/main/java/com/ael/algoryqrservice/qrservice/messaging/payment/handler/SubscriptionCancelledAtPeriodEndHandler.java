package com.ael.algoryqrservice.qrservice.messaging.payment.handler;

import com.ael.algoryqrservice.qrservice.messaging.payment.PaymentEventTypes;
import com.ael.algoryqrservice.qrservice.billing.dto.PaymentCompletedEventDto;
import com.ael.algoryqrservice.qrservice.billing.service.PurchaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
@Component
@RequiredArgsConstructor
public class SubscriptionCancelledAtPeriodEndHandler implements PaymentEventHandler {

    private final PurchaseService purchaseService;

    @Override
    public boolean supports(String eventType) {
        return PaymentEventTypes.SUBSCRIPTION_CANCELLED_AT_PERIOD_END.equals(eventType);
    }

    @Override
    public void handle(PaymentCompletedEventDto event) {
        purchaseService.handleSubscriptionCancelledAtPeriodEnd(event);
    }
}
