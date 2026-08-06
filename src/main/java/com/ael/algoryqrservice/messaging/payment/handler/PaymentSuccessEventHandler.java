package com.ael.algoryqrservice.messaging.payment.handler;

import com.ael.algoryqrservice.messaging.payment.PaymentEventTypes;
import com.ael.algoryqrservice.model.dto.PaymentCompletedEventDto;
import com.ael.algoryqrservice.service.PurchaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class PaymentSuccessEventHandler implements PaymentEventHandler {

    private static final Set<String> SUPPORTED = Set.of(
            PaymentEventTypes.PAYMENT_SUCCESS,
            PaymentEventTypes.PAYMENT_INSTALLMENT_PAID,
            PaymentEventTypes.PAYMENT_SUBSCRIPTION_PAID
    );

    private final PurchaseService purchaseService;

    @Override
    public boolean supports(String eventType) {
        return SUPPORTED.contains(eventType);
    }

    @Override
    public void handle(PaymentCompletedEventDto event) {
        purchaseService.handlePaymentSuccess(event);
    }
}
