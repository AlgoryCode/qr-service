package com.ael.algoryqrservice.messaging.payment.conversion;

import com.ael.algoryqrservice.model.dto.PaymentCompletedEventDto;
import org.springframework.amqp.core.Message;

public interface PaymentEventPayloadConverter {

    PaymentCompletedEventDto convert(Message message);
}
