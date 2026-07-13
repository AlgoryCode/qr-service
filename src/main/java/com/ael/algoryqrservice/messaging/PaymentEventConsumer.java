package com.ael.algoryqrservice.messaging;

import com.ael.algoryqrservice.model.dto.PaymentCompletedEventDto;
import com.ael.algoryqrservice.service.PurchaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

    private final PurchaseService purchaseService;

    @RabbitListener(queues = "#{paymentRabbitMqProperties.successQueue}")
    public void onPaymentSuccess(PaymentCompletedEventDto event) {
        log.info("Payment success event received. eventId={} purchaseId={}", event.getEventId(), event.getSourceReferenceId());
        purchaseService.handlePaymentSuccess(event);
    }

    @RabbitListener(queues = "#{paymentRabbitMqProperties.failedQueue}")
    public void onPaymentFailed(PaymentCompletedEventDto event) {
        log.info("Payment failed event received. eventId={} purchaseId={}", event.getEventId(), event.getSourceReferenceId());
        purchaseService.handlePaymentFailed(event);
    }
}
