package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogProducts;
import com.ael.algoryqrservice.catalog.CatalogScopes;
import com.ael.algoryqrservice.model.Product;
import com.ael.algoryqrservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PackageCatalogService {

    private final ProductRepository productRepository;

    @Transactional
    public void ensureCatalogProducts() {
        ensureProduct(CatalogProducts.QR_CREATE, "QR Olusturma", CatalogScopes.QR_CREATE_OWNER, true);
        ensureProduct(CatalogProducts.QR_MENU, "QR Menu", CatalogScopes.QR_MENU_OWNER, true);
        ensureProduct(CatalogProducts.MENU_PRODUCT, "Menu Urun Hakki", CatalogScopes.MENU_PRODUCT_OWNER, true);
        ensureProduct(CatalogProducts.SMART_ASSISTANT, "Akilli Asistan", CatalogScopes.SMART_ASSISTANT_OWNER, false);
        ensureProduct(CatalogProducts.SMART_SUMMARY, "Akilli Ozet", CatalogScopes.SMART_SUMMARY_OWNER, false);
        ensureProduct(CatalogProducts.SMART_REPORTING, "Akilli Raporlama", CatalogScopes.SMART_REPORTING_OWNER, false);
        ensureProduct(CatalogProducts.CUSTOM_DESIGN, "Ozel Tasarim Menu", CatalogScopes.CUSTOM_DESIGN_OWNER, false);
        ensureProduct(CatalogProducts.WAITER_PANEL, "Garson Paneli", CatalogScopes.WAITER_PANEL_OWNER, false);
    }

    private Product ensureProduct(String code, String name, String scopeCode, boolean consumable) {
        return productRepository.findByCode(code).orElseGet(() -> productRepository.save(
                Product.builder()
                        .code(code)
                        .name(name)
                        .description(name + " urunu")
                        .scopeCode(scopeCode)
                        .consumable(consumable)
                        .active(true)
                        .build()
        ));
    }
}
