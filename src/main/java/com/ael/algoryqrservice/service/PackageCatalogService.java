package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogPackages;
import com.ael.algoryqrservice.catalog.CatalogProducts;
import com.ael.algoryqrservice.catalog.CatalogScopes;
import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.PlanPackageAddon;
import com.ael.algoryqrservice.model.PlanPackageItem;
import com.ael.algoryqrservice.model.Product;
import com.ael.algoryqrservice.model.enums.ProductType;
import com.ael.algoryqrservice.repository.PlanPackageAddonRepository;
import com.ael.algoryqrservice.repository.PlanPackageRepository;
import com.ael.algoryqrservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PackageCatalogService {

    private static final BigDecimal BRANCH_PRICE = new BigDecimal("600.00");
    private static final BigDecimal MENU_PRICE = new BigDecimal("200.00");
    private static final BigDecimal VAT = new BigDecimal("20.00");

    private final ProductRepository productRepository;
    private final PlanPackageRepository planPackageRepository;
    private final PlanPackageAddonRepository planPackageAddonRepository;

    @Transactional
    public void ensureCatalogProducts() {
        ensureProduct(CatalogProducts.QR_CREATE,      "QR Olusturma",       CatalogScopes.QR_CREATE_OWNER,      true,  ProductType.PACKAGE_PRODUCT, CatalogProducts.QR_CREATE,      true,  true);
        ensureProduct(CatalogProducts.QR_MENU,        "QR Menu",            CatalogScopes.QR_MENU_OWNER,        true,  ProductType.PACKAGE_PRODUCT, CatalogProducts.QR_MENU,        true,  true);
        ensureProduct(CatalogProducts.QR_BRANCH,      "Ek Sube",            CatalogScopes.QR_BRANCH_OWNER,      true,  ProductType.PACKAGE_PRODUCT, CatalogProducts.QR_BRANCH,      true,  false);
        ensureProduct(CatalogProducts.MENU_PRODUCT,   "Menu Urun Hakki",    CatalogScopes.MENU_PRODUCT_OWNER,   true,  ProductType.PACKAGE_PRODUCT, CatalogProducts.MENU_PRODUCT,   true,  true);
        ensureProduct(CatalogProducts.SMART_ASSISTANT,"Akilli Asistan",     CatalogScopes.SMART_ASSISTANT_OWNER,false, ProductType.PACKAGE_PRODUCT, CatalogProducts.SMART_ASSISTANT,false, false);
        ensureProduct(CatalogProducts.SMART_SUMMARY,  "Akilli Ozet",        CatalogScopes.SMART_SUMMARY_OWNER,  false, ProductType.PACKAGE_PRODUCT, CatalogProducts.SMART_SUMMARY,  false, false);
        ensureProduct(CatalogProducts.SMART_REPORTING,"Akilli Raporlama",   CatalogScopes.SMART_REPORTING_OWNER,false, ProductType.PACKAGE_PRODUCT, CatalogProducts.SMART_REPORTING,false, false);
        ensureProduct(CatalogProducts.CUSTOM_DESIGN,  "Ozel Tasarim Menu",  CatalogScopes.CUSTOM_DESIGN_OWNER,  false, ProductType.PACKAGE_PRODUCT, CatalogProducts.CUSTOM_DESIGN,  false, false);
        ensureProduct(CatalogProducts.WAITER_PANEL,   "Garson Paneli",      CatalogScopes.WAITER_PANEL_OWNER,   false, ProductType.PACKAGE_PRODUCT, CatalogProducts.WAITER_PANEL,   false, false);
        ensureProduct(CatalogProducts.QR_MENU_ADDON,  "Ek Dijital Menu",    CatalogScopes.QR_MENU_OWNER,        true,  ProductType.ADDON_PRODUCT,   CatalogProducts.QR_MENU,        true,  false);
        ensureProduct(CatalogProducts.QR_BRANCH_ADDON,"Ek Sube Hakki",      CatalogScopes.QR_BRANCH_OWNER,      true,  ProductType.ADDON_PRODUCT,   CatalogProducts.QR_BRANCH,      true,  false);
        ensureBranchBilling();
    }

    @Transactional
    public void ensureBranchBilling() {
        upsertAddonPrice(CatalogProducts.QR_BRANCH_ADDON, "Ek Sube", "Ek sube olusturma hakki", CatalogScopes.QR_BRANCH_OWNER, CatalogProducts.QR_BRANCH, BRANCH_PRICE);
        upsertAddonPrice(CatalogProducts.QR_MENU_ADDON, "QR Menu", "Ek dijital menu olusturma hakki", CatalogScopes.QR_MENU_OWNER, CatalogProducts.QR_MENU, MENU_PRICE);
        upsertPrice(CatalogProducts.QR_BRANCH, "Ek Sube", "Ek sube olusturma hakki", CatalogScopes.QR_BRANCH_OWNER, true, BRANCH_PRICE);
        upsertPrice(CatalogProducts.QR_MENU, "QR Menu", "Ek dijital menu olusturma hakki", CatalogScopes.QR_MENU_OWNER, true, MENU_PRICE);
        syncPackage(CatalogPackages.STARTER_PACKAGE, List.of("50 urun hakki", "1 ucretsiz sube", "Sube basi 1 ucretsiz menu", "Standart sablonlar"));
        syncPackage(CatalogPackages.PRO_PACKAGE, List.of("Sinirsiz urun hakki", "1 ucretsiz sube", "Sube basi 1 ucretsiz menu", "Ciro takibi ve gelir raporlamasi"));
        syncPackage(CatalogPackages.ULTIMATE_PACKAGE, List.of(
                "1 ucretsiz sube",
                "Sube basi 1 ucretsiz menu",
                "Garson siparis ve adisyon modulu",
                "Ciro takibi ve gelismis raporlar",
                "Haftalik akilli raporlama",
                "Akilli asistan",
                "Akilli ozet",
                "Ozel tasarim menu"
        ));
    }

    private void syncPackage(String packageCode, List<String> features) {
        PlanPackage planPackage = planPackageRepository.findByCode(packageCode)
                .flatMap(existing -> planPackageRepository.findByIdWithItems(existing.getId()))
                .orElse(null);
        if (planPackage == null) {
            return;
        }
        planPackage.setFeatures(new ArrayList<>(features));
        Product branch = productRepository.findByCode(CatalogProducts.QR_BRANCH).orElse(null);
        if (branch != null && planPackage.getItems().stream()
                .noneMatch(item -> item.getProduct().getId().equals(branch.getId()))) {
            planPackage.getItems().add(PlanPackageItem.builder()
                    .planPackage(planPackage)
                    .product(branch)
                    .quantity(1)
                    .unlimited(false)
                    .build());
        }
        for (PlanPackageItem item : planPackage.getItems()) {
            if (CatalogProducts.QR_MENU.equals(item.getProduct().getFeatureCode()) && item.isUnlimited()) {
                item.setUnlimited(false);
                item.setQuantity(1);
            }
        }
        planPackageRepository.save(planPackage);
        ensurePackageAddon(planPackage, CatalogProducts.QR_MENU_ADDON);
        ensurePackageAddon(planPackage, CatalogProducts.QR_BRANCH_ADDON);
    }

    private void upsertPrice(
            String code,
            String name,
            String description,
            String scopeCode,
            boolean consumable,
            BigDecimal unitPrice
    ) {
        Product product = productRepository.findByCode(code).orElseGet(() -> Product.builder()
                .code(code)
                .active(true)
                .build());
        product.setName(name);
        product.setDescription(description);
        product.setScopeCode(scopeCode);
        product.setConsumable(consumable);
        product.setActive(true);
        product.setUnitPrice(unitPrice);
        product.setVatRate(VAT);
        productRepository.save(product);
    }

    private void upsertAddonPrice(
            String code,
            String name,
            String description,
            String scopeCode,
            String featureCode,
            BigDecimal unitPrice
    ) {
        Product product = productRepository.findByCode(code).orElseGet(() -> Product.builder()
                .code(code)
                .active(true)
                .build());
        product.setName(name);
        product.setDescription(description);
        product.setScopeCode(scopeCode);
        product.setFeatureCode(featureCode);
        product.setTypeId(ProductType.ADDON_PRODUCT);
        product.setConsumable(true);
        product.setActive(true);
        product.setUnitPrice(unitPrice);
        product.setVatRate(VAT);
        productRepository.save(product);
    }

    private Product ensureProduct(String code, String name, String scopeCode, boolean consumable,
                                  ProductType typeId, String featureCode,
                                  boolean addonPurchasable, boolean requiresCountSync) {
        Product product = productRepository.findByCode(code).orElseGet(() -> Product.builder()
                .code(code)
                .name(name)
                .description(name + " urunu")
                .scopeCode(scopeCode)
                .consumable(consumable)
                .active(true)
                .build());
        if (product.getTypeId() == null) {
            product.setTypeId(typeId);
        }
        if (product.getFeatureCode() == null && featureCode != null) {
            product.setFeatureCode(featureCode);
        }
        product.setAddonPurchasable(addonPurchasable);
        product.setRequiresCountSync(requiresCountSync);
        return productRepository.save(product);
    }

    public void ensurePackageAddon(PlanPackage planPackage, String addonProductCode) {
        productRepository.findByCode(addonProductCode).ifPresent(product -> {
            planPackageAddonRepository.findByPackageIdAndProductId(planPackage.getId(), product.getId())
                    .orElseGet(() -> planPackageAddonRepository.save(PlanPackageAddon.builder()
                            .planPackage(planPackage)
                            .product(product)
                            .active(true)
                            .build()));
        });
    }
}
