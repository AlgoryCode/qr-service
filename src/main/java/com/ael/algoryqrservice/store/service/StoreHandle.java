package com.ael.algoryqrservice.store.service;

/**
 * Parsed storefront handle {@code {storeNo}-{publicToken}}.
 * Only these two parts identify a merchant; a leftover slug in older links is ignored.
 */
public record StoreHandle(Long storeNo, String token) {
}
