package com.ael.algoryqrservice.qrservice.messaging.payment.conversion;

import com.ael.algoryqrservice.qrservice.billing.dto.PaymentCompletedEventDto;
import org.springframework.amqp.core.Message;

public interface PaymentEventPayloadConverter {

    PaymentCompletedEventDto convert(Message message);
}
