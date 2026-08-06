package com.ael.algoryqrservice.messaging.payment.handler;

import com.ael.algoryqrservice.messaging.payment.PaymentEventTypes;
import com.ael.algoryqrservice.model.dto.PaymentCompletedEventDto;
import com.ael.algoryqrservice.service.PurchaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentOverdueEventHandler implements PaymentEventHandler {

    private final PurchaseService purchaseService;

    @Override
    public boolean supports(String eventType) {
        return PaymentEventTypes.PAYMENT_INSTALLMENT_OVERDUE.equals(eventType);
    }

    @Override
    public void handle(PaymentCompletedEventDto event) {
        purchaseService.handlePaymentOverdue(event);
    }
}
