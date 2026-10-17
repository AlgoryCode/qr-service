package com.ael.algoryqrservice.paymentservice.infrastructure.messaging.adapter;

import com.ael.algoryqrservice.paymentservice.domain.model.PaymentCompletedEvent;
import com.ael.algoryqrservice.paymentservice.domain.port.PaymentEventPublisherPort;
import com.ael.algoryqrservice.paymentservice.infrastructure.messaging.PaymentEventOutboxPublisher;
import com.ael.algoryqrservice.paymentservice.infrastructure.messaging.PaymentRoutingKeyResolver;
import com.ael.algoryqrservice.paymentservice.infrastructure.persistence.entity.PaymentEventOutboxEntity;
import com.ael.algoryqrservice.paymentservice.infrastructure.persistence.repository.PaymentEventOutboxJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPaymentEventPublisherAdapter implements PaymentEventPublisherPort {

    private final PaymentEventOutboxJpaRepository outboxRepository;
    private final PaymentRoutingKeyResolver routingKeyResolver;
    private final JsonMapper jsonMapper;
    private final PaymentEventOutboxPublisher outboxPublisher;

    @Override
    public void publish(PaymentCompletedEvent event, String serviceName, boolean success) {
        if (outboxRepository.existsByEventId(event.getEventId())) {
            triggerImmediatePublish(event.getEventId());
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        PaymentEventOutboxEntity entity = PaymentEventOutboxEntity.builder()
                .eventId(event.getEventId())
                .aggregateId(event.getConversationId())
                .eventType(event.getEventType())
                .serviceName(serviceName)
                .routingKey(routingKeyResolver.resolve(serviceName))
                .payload(writePayload(event))
                .status("PENDING")
                .retryCount(0)
                .nextAttemptAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
        outboxRepository.save(entity);
        triggerImmediatePublish(event.getEventId());
    }

    private void triggerImmediatePublish(String eventId) {
        Runnable publish = () -> {
            try {
                outboxPublisher.publishByEventId(eventId);
            } catch (RuntimeException exception) {
                log.warn(
                        "Immediate payment outbox publish failed; scheduler will retry. eventId={} reason={}",
                        eventId,
                        exception.getMessage()
                );
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish.run();
                }
            });
            return;
        }
        publish.run();
    }

    private String writePayload(PaymentCompletedEvent event) {
        try {
            return jsonMapper.writeValueAsString(event);
        } catch (Exception exception) {
            throw new IllegalStateException("Payment event cannot be serialized", exception);
        }
    }
}
