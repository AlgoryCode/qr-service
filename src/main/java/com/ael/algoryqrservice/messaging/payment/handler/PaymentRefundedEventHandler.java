package com.ael.algoryqrservice.messaging.payment.handler;

import com.ael.algoryqrservice.messaging.payment.PaymentEventTypes;
import com.ael.algoryqrservice.model.dto.PaymentCompletedEventDto;
import com.ael.algoryqrservice.service.PurchaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class PaymentRefundedEventHandler implements PaymentEventHandler {

    private static final Set<String> SUPPORTED = Set.of(
            PaymentEventTypes.PAYMENT_REFUNDED,
            PaymentEventTypes.PAYMENT_CHARGEBACK
    );

    private final PurchaseService purchaseService;

    @Override
    public boolean supports(String eventType) {
        return SUPPORTED.contains(eventType);
    }

    @Override
    public void handle(PaymentCompletedEventDto event) {
        purchaseService.handlePaymentRefunded(event);
    }
}
