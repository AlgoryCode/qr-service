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

    @RabbitListener(queues = "#{paymentRabbitMqProperties.eventsQueue}")
    public void onPaymentEvent(PaymentCompletedEventDto event) {
        log.info(
                "Payment event consumed. eventId={} eventType={} purchaseId={} conversationId={} amount={} currency={} failureReason={}",
                event.getEventId(),
                event.getEventType(),
                event.getSourceReferenceId(),
                event.getConversationId(),
                event.getAmount(),
                event.getCurrency(),
                event.getFailureReason()
        );
        consume(event);
    }

    private void consume(PaymentCompletedEventDto event) {
        try {
            switch (event.getEventType()) {
                case "payment.installment.paid", "payment.success", "payment.subscription.paid"
                        -> purchaseService.handlePaymentSuccess(event);
                case "payment.installment.failed", "payment.failed",
                     "payment.subscription.failed", "payment.subscription.past_due"
                        -> purchaseService.handlePaymentFailed(event);
                case "payment.installment.overdue" -> purchaseService.handlePaymentOverdue(event);
                case "payment.refunded", "payment.chargeback" -> purchaseService.handlePaymentRefunded(event);
                default -> throw new InvalidPaymentEventException("Unsupported payment event type");
            }
            log.info(
                    "Payment event processed. eventId={} eventType={} purchaseId={}",
                    event.getEventId(),
                    event.getEventType(),
                    event.getSourceReferenceId()
            );
        } catch (InvalidPaymentEventException exception) {
            log.error(
                    "Payment event rejected. eventId={} eventType={} purchaseId={} reason={}",
                    event.getEventId(),
                    event.getEventType(),
                    event.getSourceReferenceId(),
                    exception.getMessage(),
                    exception
            );
            throw new AmqpRejectAndDontRequeueException(exception.getMessage(), exception);
        } catch (RuntimeException exception) {
            log.error(
                    "Payment event processing failed. eventId={} eventType={} purchaseId={}",
                    event.getEventId(),
                    event.getEventType(),
                    event.getSourceReferenceId(),
                    exception
            );
            throw exception;
        }
    }
}
