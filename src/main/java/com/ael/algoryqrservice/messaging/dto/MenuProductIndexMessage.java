package com.ael.algoryqrservice.messaging.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * Wire contract published to the menu events exchange whenever a menu product changes.
 * Consumed by ai-service to keep the {@code menu_products} vector collection in sync.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MenuProductIndexMessage(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        Long menuId,
        Long productId,
        MenuProductDocumentMessage document
) {

    public static final String EVENT_UPSERTED = "PRODUCT_UPSERTED";
    public static final String EVENT_DELETED = "PRODUCT_DELETED";
    public static final String EVENT_MENU_PURGED = "MENU_PURGED";

    public static MenuProductIndexMessage upserted(MenuProductDocumentMessage document) {
        return new MenuProductIndexMessage(
                UUID.randomUUID(),
                EVENT_UPSERTED,
                Instant.now(),
                document.menuId(),
                document.productId(),
                document
        );
    }

    public static MenuProductIndexMessage deleted(Long menuId, Long productId) {
        return new MenuProductIndexMessage(
                UUID.randomUUID(),
                EVENT_DELETED,
                Instant.now(),
                menuId,
                productId,
                null
        );
    }

    public static MenuProductIndexMessage menuPurged(Long menuId) {
        return new MenuProductIndexMessage(
                UUID.randomUUID(),
                EVENT_MENU_PURGED,
                Instant.now(),
                menuId,
                null,
                null
        );
    }
}
