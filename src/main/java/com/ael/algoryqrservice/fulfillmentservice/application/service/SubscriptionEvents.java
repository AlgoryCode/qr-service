package com.ael.algoryqrservice.fulfillmentservice.application.service;

import com.ael.algoryqrservice.fulfillmentservice.domain.model.SubscriptionEvent;
import com.ael.algoryqrservice.fulfillmentservice.domain.port.SubscriptionHistoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Records a subscription change in history and publishes it in the same transaction. Listeners
 * that send mail or touch other modules use {@code @TransactionalEventListener}, so they only run
 * once the change is committed.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionEvents {

    private final SubscriptionHistoryPort history;
    private final ApplicationEventPublisher publisher;

    /** Returns false (and publishes nothing) when the event's dedupe key was already recorded. */
    public boolean emit(SubscriptionEvent event) {
        if (event == null || event.merchantId() == null) {
            return false;
        }
        if (!history.record(event)) {
            return false;
        }
        log.info("subscription_event merchantId={} type={} package={} previous={} status={} endsAt={}",
                event.merchantId(), event.type(), event.packageCode(), event.previousPackageCode(),
                event.status(), event.endsAt());
        publisher.publishEvent(event);
        return true;
    }
}
