package com.ael.algoryqrservice.fulfillmentservice.domain.model;

/**
 * @param consumable spent and gone (report credits); its used counter starts over each period. A
 *                   non-consumable counts things that still exist (branches, menus, products), so its
 *                   counter is carried across renewals and package changes.
 * @param recurring  billed every period together with the package (a monthly module), as opposed to
 *                   a one-time purchase
 */
public record CatalogProduct(
        Long id,
        String code,
        String name,
        String featureCode,
        String scopeCode,
        boolean consumable,
        boolean recurring
) {

    public CatalogProduct(Long id, String code, String name, String featureCode, String scopeCode) {
        this(id, code, name, featureCode, scopeCode, false, false);
    }
}
