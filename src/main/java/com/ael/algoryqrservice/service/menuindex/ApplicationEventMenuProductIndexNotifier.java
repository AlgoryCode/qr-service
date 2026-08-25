package com.ael.algoryqrservice.service.menuindex;

import com.ael.algoryqrservice.messaging.dto.MenuProductDocumentMessage;
import com.ael.algoryqrservice.model.MenuProduct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/**
 * Builds the outgoing document while the entity is still managed, then hands it to the
 * Spring event bus. Delivery to RabbitMQ happens after the transaction commits.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApplicationEventMenuProductIndexNotifier implements MenuProductIndexNotifier {

    private final ApplicationEventPublisher eventPublisher;
    private final MenuProductDocumentFactory documentFactory;

    @Override
    public void productChanged(MenuProduct product) {
        if (product == null || product.getProductId() == null) {
            return;
        }
        if (product.isDeleted()) {
            productRemoved(product.getMenuId(), product.getProductId());
            return;
        }
        publish(List.of(documentFactory.create(product)));
    }

    @Override
    public void productsChanged(Collection<MenuProduct> products) {
        if (products == null || products.isEmpty()) {
            return;
        }
        List<MenuProduct> indexable = products.stream()
                .filter(product -> product != null && product.getProductId() != null && !product.isDeleted())
                .toList();
        publish(documentFactory.createAll(indexable));
    }

    @Override
    public void productRemoved(Long menuId, Long productId) {
        if (menuId == null || productId == null) {
            return;
        }
        eventPublisher.publishEvent(new MenuProductIndexEvents.Removed(menuId, productId));
    }

    @Override
    public void menuRemoved(Long menuId) {
        if (menuId == null) {
            return;
        }
        eventPublisher.publishEvent(new MenuProductIndexEvents.MenuPurged(menuId));
    }

    private void publish(List<MenuProductDocumentMessage> documents) {
        if (documents.isEmpty()) {
            return;
        }
        eventPublisher.publishEvent(new MenuProductIndexEvents.Upserted(documents));
    }
}
