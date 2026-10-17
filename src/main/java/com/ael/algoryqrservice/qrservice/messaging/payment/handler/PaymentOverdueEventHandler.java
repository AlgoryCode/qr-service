package com.ael.algoryqrservice.qrservice.messaging.payment.handler;

import com.ael.algoryqrservice.qrservice.messaging.payment.PaymentEventTypes;
import com.ael.algoryqrservice.qrservice.billing.dto.PaymentCompletedEventDto;
import com.ael.algoryqrservice.qrservice.billing.service.PurchaseService;
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
