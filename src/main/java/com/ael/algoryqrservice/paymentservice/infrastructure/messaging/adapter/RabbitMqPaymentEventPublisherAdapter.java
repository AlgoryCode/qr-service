package com.ael.algoryqrservice.paymentservice.infrastructure.messaging.adapter;

import com.ael.algoryqrservice.paymentservice.domain.model.PaymentCompletedEvent;
import com.ael.algoryqrservice.paymentservice.infrastructure.config.PaymentEventsRabbitMqProperties;
import com.ael.algoryqrservice.paymentservice.infrastructure.messaging.PaymentEventWireContract;
import com.ael.algoryqrservice.paymentservice.infrastructure.messaging.PaymentRoutingKeyResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitMqPaymentEventPublisherAdapter {

    private final RabbitTemplate rabbitTemplate;
    private final PaymentEventsRabbitMqProperties paymentEventsRabbitMqProperties;
    private final PaymentRoutingKeyResolver paymentRoutingKeyResolver;
    private final JsonMapper jsonMapper;

    public boolean publishConfirmed(PaymentCompletedEvent event, String serviceName, boolean success) {
        return publishConfirmed(event, paymentRoutingKeyResolver.resolve(serviceName));
    }

    public boolean publishConfirmed(PaymentCompletedEvent event, String routingKey) {
        Message message = toWireMessage(event);
        Boolean confirmed = rabbitTemplate.invoke(operations -> {
            operations.send(
                    paymentEventsRabbitMqProperties.exchange(),
                    routingKey,
                    message
            );
            return operations.waitForConfirms(5000);
        });
        if (confirmed == null || !confirmed) {
            return false;
        }
        log.info("Payment event published. routingKey={} eventId={}", routingKey, event.getEventId());
        return true;
    }

    private Message toWireMessage(PaymentCompletedEvent event) {
        byte[] body;
        try {
            body = jsonMapper.writeValueAsBytes(event);
        } catch (Exception exception) {
            throw new IllegalStateException("Payment event cannot be serialized for RabbitMQ", exception);
        }
        MessageProperties properties = new MessageProperties();
        properties.setContentType(PaymentEventWireContract.CONTENT_TYPE_JSON);
        properties.setContentEncoding(StandardCharsets.UTF_8.name());
        if (event.getEventType() != null && !event.getEventType().isBlank()) {
            properties.setHeader(
                    PaymentEventWireContract.HEADER_EVENT_TYPE,
                    event.getEventType()
            );
        }
        String purchaseId = event.getSourceReferenceId() != null
                ? event.getSourceReferenceId()
                : event.getPurchaseId();
        if (purchaseId != null && !purchaseId.isBlank()) {
            properties.setHeader(
                    PaymentEventWireContract.HEADER_PURCHASE_ID,
                    purchaseId
            );
        }
        return new Message(body, properties);
    }
}
