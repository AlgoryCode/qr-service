package com.ael.algoryqrservice.messaging;

import com.ael.algoryqrservice.model.dto.PaymentCompletedEventDto;
import com.ael.algoryqrservice.exception.InvalidPaymentEventException;
import com.ael.algoryqrservice.service.PurchaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

    private final PurchaseService purchaseService;

    @RabbitListener(queues = "#{paymentRabbitMqProperties.successQueue}")
    public void onPaymentSuccess(PaymentCompletedEventDto event) {
        log.info("Payment success event received. eventId={} purchaseId={}", event.getEventId(), event.getSourceReferenceId());
        consume(event);
    }

    @RabbitListener(queues = "#{paymentRabbitMqProperties.failedQueue}")
    public void onPaymentFailed(PaymentCompletedEventDto event) {
        log.info("Payment failed event received. eventId={} purchaseId={}", event.getEventId(), event.getSourceReferenceId());
        consume(event);
    }

    private void consume(PaymentCompletedEventDto event) {
        try {
            switch (event.getEventType()) {
                case "payment.installment.paid", "payment.success" -> purchaseService.handlePaymentSuccess(event);
                case "payment.installment.failed", "payment.failed" -> purchaseService.handlePaymentFailed(event);
                case "payment.installment.overdue" -> purchaseService.handlePaymentOverdue(event);
                case "payment.refunded", "payment.chargeback" -> purchaseService.handlePaymentRefunded(event);
                default -> throw new InvalidPaymentEventException("Unsupported payment event type");
            }
        } catch (InvalidPaymentEventException exception) {
            throw new AmqpRejectAndDontRequeueException(exception.getMessage(), exception);
        }
    }
}
