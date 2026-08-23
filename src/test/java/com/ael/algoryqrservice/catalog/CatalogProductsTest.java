package com.ael.algoryqrservice.catalog;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogProductsTest {

    @Test
    void isAddonPurchasable_whenCountableProduct_thenTrue() {
        assertThat(CatalogProducts.isAddonPurchasable(CatalogProducts.QR_MENU)).isTrue();
        assertThat(CatalogProducts.isAddonPurchasable(CatalogProducts.QR_BRANCH)).isTrue();
        assertThat(CatalogProducts.isAddonPurchasable(CatalogProducts.MENU_PRODUCT)).isTrue();
        assertThat(CatalogProducts.isAddonPurchasable(CatalogProducts.QR_CREATE)).isTrue();
    }

    @Test
    void isAddonPurchasable_whenFeatureProduct_thenFalse() {
        assertThat(CatalogProducts.isAddonPurchasable(CatalogProducts.SMART_ASSISTANT)).isFalse();
        assertThat(CatalogProducts.isAddonPurchasable("UNKNOWN")).isFalse();
    }
}
