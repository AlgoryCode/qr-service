package com.ael.algoryqrservice.service.menuindex;

import com.ael.algoryqrservice.config.MenuEventsRabbitProperties;
import com.ael.algoryqrservice.messaging.dto.MenuProductDocumentMessage;
import com.ael.algoryqrservice.messaging.dto.MenuProductIndexMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends product index messages only once the writing transaction has committed, so a
 * rolled-back product edit can never reach the search index.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MenuProductIndexPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final MenuEventsRabbitProperties properties;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProductsUpserted(MenuProductIndexEvents.Upserted event) {
        for (MenuProductDocumentMessage document : event.documents()) {
            send(MenuProductIndexMessage.upserted(document));
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProductRemoved(MenuProductIndexEvents.Removed event) {
        send(MenuProductIndexMessage.deleted(event.menuId(), event.productId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMenuPurged(MenuProductIndexEvents.MenuPurged event) {
        send(MenuProductIndexMessage.menuPurged(event.menuId()));
    }

    private void send(MenuProductIndexMessage message) {
        if (!properties.isPublishEnabled()) {
            return;
        }
        try {
            rabbitTemplate.convertAndSend(
                    properties.getExchange(),
                    properties.getProductIndexRoutingKey(),
                    message
            );
        } catch (Exception ex) {
            log.error(
                    "Menu product index event could not be published. menuId={} productId={} type={}",
                    message.menuId(),
                    message.productId(),
                    message.eventType(),
                    ex
            );
        }
    }
}
