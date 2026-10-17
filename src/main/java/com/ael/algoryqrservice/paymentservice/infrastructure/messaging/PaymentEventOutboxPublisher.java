package com.ael.algoryqrservice.paymentservice.infrastructure.messaging;

import com.ael.algoryqrservice.paymentservice.domain.model.PaymentCompletedEvent;
import com.ael.algoryqrservice.paymentservice.infrastructure.persistence.entity.PaymentEventOutboxEntity;
import com.ael.algoryqrservice.paymentservice.infrastructure.persistence.repository.PaymentEventOutboxJpaRepository;
import com.ael.algoryqrservice.paymentservice.application.PaymentModuleQrEventBridge;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
public class PaymentEventOutboxPublisher {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final int BATCH_SIZE = 50;
    private static final int MAX_BACKOFF_MINUTES = 60;

    private final PaymentEventOutboxJpaRepository outboxRepository;
    private final JsonMapper jsonMapper;
    private final PaymentModuleQrEventBridge inProcessBridge;

    public PaymentEventOutboxPublisher(
            PaymentEventOutboxJpaRepository outboxRepository,
            JsonMapper jsonMapper,
            @Lazy PaymentModuleQrEventBridge inProcessBridge
    ) {
        this.outboxRepository = outboxRepository;
        this.jsonMapper = jsonMapper;
        this.inProcessBridge = inProcessBridge;
    }

    @Scheduled(fixedDelayString = "${payment.outbox.fixed-delay:5000}")
    @Transactional
    public void publishPendingEvents() {
        List<PaymentEventOutboxEntity> events =
                outboxRepository.findByStatusAndNextAttemptAtLessThanEqualOrderById(
                        STATUS_PENDING,
                        LocalDateTime.now(),
                        PageRequest.of(0, BATCH_SIZE)
                );
        events.forEach(this::publish);
    }

    @Transactional
    public void publishByEventId(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return;
        }
        outboxRepository.findByEventId(eventId).ifPresent(entity -> {
            if (!STATUS_PENDING.equals(entity.getStatus())) {
                return;
            }
            publish(entity);
        });
    }

    private void publish(PaymentEventOutboxEntity entity) {
        try {
            PaymentCompletedEvent event = jsonMapper.readValue(entity.getPayload(), PaymentCompletedEvent.class);
            publishWire(event);
            LocalDateTime now = LocalDateTime.now();
            entity.setStatus(STATUS_PUBLISHED);
            entity.setPublishedAt(now);
            entity.setUpdatedAt(now);
            entity.setLastError(null);
            log.info("Payment outbox event published. eventId={} routingKey={}", entity.getEventId(), entity.getRoutingKey());
        } catch (Exception exception) {
            scheduleRetry(entity, exception);
        }
    }

    private void publishWire(PaymentCompletedEvent event) {
        inProcessBridge.dispatch(event);
    }

    private void scheduleRetry(PaymentEventOutboxEntity entity, Exception exception) {
        int retryCount = entity.getRetryCount() + 1;
        long backoffMinutes = Math.min(1L << Math.min(retryCount - 1, 6), MAX_BACKOFF_MINUTES);
        entity.setRetryCount(retryCount);
        entity.setNextAttemptAt(LocalDateTime.now().plusMinutes(backoffMinutes));
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setLastError(limit(exception.getMessage(), 1000));
        log.warn("Payment outbox publish failed. eventId={} retryCount={}", entity.getEventId(), retryCount);
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
