package com.ael.algoryqrservice.service.menuindex;

import com.ael.algoryqrservice.messaging.dto.MenuProductDocumentMessage;

import java.util.List;

/**
 * In-process events raised while a product transaction is still open.
 * They are turned into RabbitMQ messages only after the transaction commits.
 */
public final class MenuProductIndexEvents {

    private MenuProductIndexEvents() {
    }

    public record Upserted(List<MenuProductDocumentMessage> documents) {
    }

    public record Removed(Long menuId, Long productId) {
    }

    public record MenuPurged(Long menuId) {
    }
}
