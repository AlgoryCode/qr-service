package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogPackages;
import com.ael.algoryqrservice.catalog.CatalogProducts;
import com.ael.algoryqrservice.catalog.CatalogScopes;
import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.PlanPackageItem;
import com.ael.algoryqrservice.model.Product;
import com.ael.algoryqrservice.repository.PlanPackageRepository;
import com.ael.algoryqrservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PackageCatalogService {

    private static final int FREE_QR_CREATE_QUANTITY = 5;
    private static final int FREE_VALIDITY_DAYS = 36_500;
    private static final int FREE_PRIORITY = 1;

    private final ProductRepository productRepository;
    private final PlanPackageRepository planPackageRepository;

    @Transactional
    public PlanPackage ensureFreePackage() {
        Product qrCreate = ensureProduct(
                CatalogProducts.QR_CREATE,
                "QR Oluşturma",
                CatalogScopes.QR_CREATE_OWNER,
                true
        );
        ensureProduct(CatalogProducts.QR_MENU, "QR Menü", CatalogScopes.QR_MENU_OWNER, true);
        ensureProduct(CatalogProducts.QR_AGENT, "QR Agent", CatalogScopes.QR_AGENT_OWNER, true);
        ensureProduct(CatalogProducts.QR_ANALYTICS, "Detaylı Raporlama", CatalogScopes.QR_ANALYTICS_OWNER, false);

        return planPackageRepository.findByCode(CatalogPackages.FREE_PACKAGE)
                .orElseGet(() -> createFreePackage(qrCreate));
    }

    private Product ensureProduct(String code, String name, String scopeCode, boolean consumable) {
        return productRepository.findByCode(code).orElseGet(() -> productRepository.save(
                Product.builder()
                        .code(code)
                        .name(name)
                        .description(name + " ürünü")
                        .scopeCode(scopeCode)
                        .consumable(consumable)
                        .active(true)
                        .build()
        ));
    }

    private PlanPackage createFreePackage(Product qrCreate) {
        PlanPackage planPackage = PlanPackage.builder()
                .code(CatalogPackages.FREE_PACKAGE)
                .name("Free")
                .description("5 adet QR oluşturma hakkı")
                .price(BigDecimal.ZERO)
                .currency("TRY")
                .active(true)
                .validityDays(FREE_VALIDITY_DAYS)
                .priority(FREE_PRIORITY)
                .purchasable(false)
                .systemManaged(true)
                .trialEligible(false)
                .build();

        PlanPackageItem item = PlanPackageItem.builder()
                .planPackage(planPackage)
                .product(qrCreate)
                .quantity(FREE_QR_CREATE_QUANTITY)
                .unlimited(false)
                .build();
        planPackage.setItems(List.of(item));
        return planPackageRepository.save(planPackage);
    }
}
