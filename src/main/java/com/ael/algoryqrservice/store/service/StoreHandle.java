package com.ael.algoryqrservice.store.service;

/**
 * Parsed form of a storefront handle such as {@code 10427-kebapci-mehmet-9fK2xQ7mZa}.
 * The slug is cosmetic; only {@code storeNo} plus {@code token} identify a merchant.
 */
public record StoreHandle(Long storeNo, String slug, String token) {
}
