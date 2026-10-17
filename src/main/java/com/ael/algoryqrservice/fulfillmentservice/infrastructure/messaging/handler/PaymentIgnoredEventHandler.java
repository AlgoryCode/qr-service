package com.ael.algoryqrservice.fulfillmentservice.infrastructure.messaging.handler;

import com.ael.algoryqrservice.fulfillmentservice.domain.model.PaymentCompletedEvent;
import com.ael.algoryqrservice.qrservice.messaging.payment.PaymentEventTypes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@Slf4j
public class PaymentIgnoredEventHandler implements PaymentEventHandler {

    private static final Set<String> SUPPORTED = Set.of(
            PaymentEventTypes.PAYMENT_FAILED,
            PaymentEventTypes.PAYMENT_SUBSCRIPTION_FAILED,
            PaymentEventTypes.PAYMENT_SUBSCRIPTION_PAST_DUE,
            PaymentEventTypes.PAYMENT_REFUNDED,
            PaymentEventTypes.PAYMENT_INSTALLMENT_FAILED,
            PaymentEventTypes.PAYMENT_INSTALLMENT_OVERDUE
    );

    @Override
    public boolean supports(String eventType) {
        return SUPPORTED.contains(eventType);
    }

    @Override
    public void handle(PaymentCompletedEvent event) {
        log.info(
                "Payment event acknowledged without fulfillment. eventId={} eventType={} purchaseId={}",
                event.eventId(),
                event.eventType(),
                event.sourceReferenceId()
        );
    }
}
