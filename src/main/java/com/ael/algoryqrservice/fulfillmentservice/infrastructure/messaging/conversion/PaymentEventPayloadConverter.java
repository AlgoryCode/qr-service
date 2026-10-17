package com.ael.algoryqrservice.fulfillmentservice.infrastructure.messaging.conversion;

import com.ael.algoryqrservice.fulfillmentservice.domain.model.PaymentCompletedEvent;
import org.springframework.amqp.core.Message;

public interface PaymentEventPayloadConverter {

    PaymentCompletedEvent convert(Message message);
}
