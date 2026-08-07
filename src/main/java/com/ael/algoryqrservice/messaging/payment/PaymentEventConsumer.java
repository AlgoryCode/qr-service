package com.ael.algoryqrservice.messaging.payment;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.InvalidPaymentEventException;
import com.ael.algoryqrservice.messaging.payment.conversion.PaymentEventPayloadConverter;
import com.ael.algoryqrservice.messaging.payment.handler.PaymentEventHandlerRegistry;
import com.ael.algoryqrservice.model.dto.PaymentCompletedEventDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

    private final PaymentEventPayloadConverter payloadConverter;
    private final PaymentEventHandlerRegistry handlerRegistry;

    @RabbitListener(
            queues = "#{paymentRabbitMqProperties.eventsQueue}",
            containerFactory = "paymentListenerContainerFactory"
    )
    public void onPaymentEvent(Message message) {
        PaymentCompletedEventDto event;
        try {
            event = payloadConverter.convert(message);
        } catch (InvalidPaymentEventException exception) {
            log.error(
                    "Payment event rejected during conversion (poison → DLQ). contentType={} reason={}",
                    message.getMessageProperties() != null
                            ? message.getMessageProperties().getContentType()
                            : null,
                    exception.getMessage(),
                    exception
            );
            throw new AmqpRejectAndDontRequeueException(exception.getMessage(), exception);
        }

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
            handlerRegistry.dispatch(event);
            log.info(
                    "Payment event processed. eventId={} eventType={} purchaseId={}",
                    event.getEventId(),
                    event.getEventType(),
                    event.getSourceReferenceId()
            );
        } catch (InvalidPaymentEventException exception) {
            log.error(
                    "Payment event rejected as poison (→ DLQ). eventId={} eventType={} purchaseId={} reason={}",
                    event.getEventId(),
                    event.getEventType(),
                    event.getSourceReferenceId(),
                    exception.getMessage(),
                    exception
            );
            throw new AmqpRejectAndDontRequeueException(exception.getMessage(), exception);
        } catch (BadRequestException exception) {
            log.warn(
                    "Payment event processing deferred (retryable). eventId={} eventType={} purchaseId={} reason={}",
                    event.getEventId(),
                    event.getEventType(),
                    event.getSourceReferenceId(),
                    exception.getMessage()
            );
            throw exception;
        } catch (RuntimeException exception) {
            log.error(
                    "Payment event processing failed (retryable). eventId={} eventType={} purchaseId={}",
                    event.getEventId(),
                    event.getEventType(),
                    event.getSourceReferenceId(),
                    exception
            );
            throw exception;
        }
    }
}
