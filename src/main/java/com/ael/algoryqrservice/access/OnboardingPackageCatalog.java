package com.ael.algoryqrservice.access;

import com.ael.algoryqrservice.catalog.CatalogPackages;
import com.ael.algoryqrservice.catalog.CatalogProducts;
import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.PlanPackageItem;
import com.ael.algoryqrservice.model.Product;
import com.ael.algoryqrservice.repository.PlanPackageRepository;
import com.ael.algoryqrservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class OnboardingPackageCatalog {

    static final int VALIDITY_DAYS = SessionAccessPolicy.ACCOUNT_ONBOARDING_DAYS;
    static final int PRIORITY = 190;
    private static final List<String> FEATURES = List.of(
            "1 ucretsiz sube",
            "Sube basi 1 ucretsiz menu",
            "Garson siparis ve adisyon modulu",
            "Ciro takibi ve gelismis raporlar",
            "Haftalik akilli raporlama",
            "Akilli asistan",
            "Akilli ozet",
            "Ozel tasarim menu",
            "AI ile menu fotografından urun ekleme"
    );
    private static final List<ItemSpec> DEFAULT_ITEMS = List.of(
            new ItemSpec(CatalogProducts.QR_CREATE, 1, true),
            new ItemSpec(CatalogProducts.QR_BRANCH, 1, false),
            new ItemSpec(CatalogProducts.QR_MENU, 1, false),
            new ItemSpec(CatalogProducts.MENU_PRODUCT, 1, true),
            new ItemSpec(CatalogProducts.SMART_REPORTING, 1, true),
            new ItemSpec(CatalogProducts.SMART_ASSISTANT, 1, true),
            new ItemSpec(CatalogProducts.SMART_SUMMARY, 1, true),
            new ItemSpec(CatalogProducts.CUSTOM_DESIGN, 1, true),
            new ItemSpec(CatalogProducts.WAITER_PANEL, 1, true),
            new ItemSpec(CatalogProducts.AI_MENU_IMPORT, 1, true)
    );

    private final PlanPackageRepository planPackageRepository;
    private final ProductRepository productRepository;

    @Transactional
    public PlanPackage ensureDefined() {
        PlanPackage trial = planPackageRepository.findByCodeWithItems(CatalogPackages.ULTIMATE_TRIAL_PACKAGE)
                .orElseGet(this::newPackage);
        applyDefinition(trial);
        replaceItems(trial, sourceItems());
        PlanPackage saved = planPackageRepository.save(trial);
        if (saved.getItems() == null || saved.getItems().isEmpty()) {
            throw new BadRequestException("Onboarding paketi bulunamadi");
        }
        return saved;
    }

    private PlanPackage newPackage() {
        return PlanPackage.builder()
                .code(CatalogPackages.ULTIMATE_TRIAL_PACKAGE)
                .items(new ArrayList<>())
                .features(new ArrayList<>())
                .build();
    }

    private void applyDefinition(PlanPackage trial) {
        trial.setName("Ultimate Deneme");
        trial.setDescription("15 gunluk Ultimate deneme paketi");
        trial.setFeatures(new ArrayList<>(FEATURES));
        trial.setPrice(BigDecimal.ZERO);
        trial.setSubtotal(BigDecimal.ZERO);
        trial.setVatAmount(BigDecimal.ZERO);
        trial.setMonthlyDiscount(BigDecimal.ZERO);
        trial.setYearlyPrice(null);
        trial.setYearlyDiscount(BigDecimal.ZERO);
        trial.setCurrency("TRY");
        trial.setActive(true);
        trial.setValidityDays(VALIDITY_DAYS);
        trial.setPriority(PRIORITY);
        trial.setPurchasable(false);
        trial.setSystemManaged(false);
    }

    private List<PlanPackageItem> sourceItems() {
        return planPackageRepository.findByCodeWithItems(CatalogPackages.ULTIMATE_PACKAGE)
                .map(PlanPackage::getItems)
                .filter(items -> items != null && !items.isEmpty())
                .orElseGet(this::defaultItems);
    }

    private List<PlanPackageItem> defaultItems() {
        List<PlanPackageItem> items = new ArrayList<>();
        for (ItemSpec spec : DEFAULT_ITEMS) {
            productRepository.findByCode(spec.code()).ifPresent(product ->
                    items.add(item(null, product, spec.quantity(), spec.unlimited()))
            );
        }
        return items;
    }

    private void replaceItems(PlanPackage trial, List<PlanPackageItem> sourceItems) {
        trial.getItems().clear();
        for (PlanPackageItem source : sourceItems) {
            trial.getItems().add(item(trial, source.getProduct(), source.getQuantity(), source.isUnlimited()));
        }
    }

    private static PlanPackageItem item(PlanPackage trial, Product product, Integer quantity, boolean unlimited) {
        return PlanPackageItem.builder()
                .planPackage(trial)
                .product(product)
                .quantity(quantity)
                .unlimited(unlimited)
                .build();
    }

    private record ItemSpec(String code, int quantity, boolean unlimited) {
    }
}
