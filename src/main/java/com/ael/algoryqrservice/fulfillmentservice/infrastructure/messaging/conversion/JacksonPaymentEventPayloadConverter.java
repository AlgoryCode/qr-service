package com.ael.algoryqrservice.fulfillmentservice.infrastructure.messaging.conversion;

import tools.jackson.databind.json.JsonMapper;
import com.ael.algoryqrservice.fulfillmentservice.domain.exception.InvalidPaymentEventException;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.PaymentCompletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component("fulfillmentJacksonPaymentEventPayloadConverter")
@RequiredArgsConstructor
public class JacksonPaymentEventPayloadConverter implements PaymentEventPayloadConverter {

    private final JsonMapper jsonMapper;

    @Override
    public PaymentCompletedEvent convert(Message message) {
        if (message == null || message.getBody() == null || message.getBody().length == 0) {
            throw new InvalidPaymentEventException("Payment event body is empty");
        }
        try {
            PaymentCompletedEvent event = jsonMapper.readValue(message.getBody(), PaymentCompletedEvent.class);
            if (!StringUtils.hasText(event.eventId())) {
                throw new InvalidPaymentEventException("Payment event eventId is required");
            }
            if (!StringUtils.hasText(event.eventType())) {
                throw new InvalidPaymentEventException("Payment event eventType is required");
            }
            return event;
        } catch (InvalidPaymentEventException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new InvalidPaymentEventException("Payment event JSON could not be parsed", exception);
        }
    }
}
