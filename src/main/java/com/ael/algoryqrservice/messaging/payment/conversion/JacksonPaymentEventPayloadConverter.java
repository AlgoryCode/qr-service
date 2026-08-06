package com.ael.algoryqrservice.messaging.payment.conversion;

import com.ael.algoryqrservice.exception.InvalidPaymentEventException;
import com.ael.algoryqrservice.model.dto.PaymentCompletedEventDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class JacksonPaymentEventPayloadConverter implements PaymentEventPayloadConverter {

    private final ObjectMapper objectMapper;

    @Override
    public PaymentCompletedEventDto convert(Message message) {
        if (message == null || message.getBody() == null || message.getBody().length == 0) {
            throw new InvalidPaymentEventException("Payment event body is empty");
        }
        try {
            PaymentCompletedEventDto event = objectMapper.readValue(message.getBody(), PaymentCompletedEventDto.class);
            if (!StringUtils.hasText(event.getEventId())) {
                throw new InvalidPaymentEventException("Payment event eventId is required");
            }
            if (!StringUtils.hasText(event.getEventType())) {
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
