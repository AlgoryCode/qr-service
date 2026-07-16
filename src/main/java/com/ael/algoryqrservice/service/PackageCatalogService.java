package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.PlanPackageItem;
import com.ael.algoryqrservice.model.Product;
import com.ael.algoryqrservice.model.enums.PackageCode;
import com.ael.algoryqrservice.model.enums.ProductCode;
import com.ael.algoryqrservice.repository.PlanPackageRepository;
import com.ael.algoryqrservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PackageCatalogService {

    private static final int FREE_QR_CREATE_QUANTITY = 5;
    private static final int FREE_VALIDITY_DAYS = 36_500;
    private static final int PRO_VALIDITY_DAYS = 30;
    private static final BigDecimal PRO_PRICE = new BigDecimal("199.00");
    private static final Set<ProductCode> PRO_PRODUCT_CODES = EnumSet.of(
            ProductCode.QR_CREATE,
            ProductCode.QR_MENU,
            ProductCode.QR_AGENT,
            ProductCode.QR_ANALYTICS
    );

    private final ProductRepository productRepository;
    private final PlanPackageRepository planPackageRepository;

    @Transactional
    public PlanPackage ensureFreePackage() {
        Product qrCreate = ensureProduct(ProductCode.QR_CREATE, "QR Oluşturma");
        ensureProduct(ProductCode.QR_MENU, "QR Menü");
        ensureProduct(ProductCode.QR_AGENT, "QR Agent");
        ensureProduct(ProductCode.QR_ANALYTICS, "Detaylı Raporlama");

        return planPackageRepository.findByCode(PackageCode.FREE_PACKAGE)
                .orElseGet(() -> createFreePackage(qrCreate));
    }

    @Transactional
    public PlanPackage ensureProPackage() {
        Product qrCreate = ensureProduct(ProductCode.QR_CREATE, "QR Oluşturma");
        Product qrMenu = ensureProduct(ProductCode.QR_MENU, "QR Menü");
        Product qrAgent = ensureProduct(ProductCode.QR_AGENT, "QR Agent");
        Product qrAnalytics = ensureProduct(ProductCode.QR_ANALYTICS, "Detaylı Raporlama");

        return planPackageRepository.findByCode(PackageCode.PRO_PACKAGE)
                .map(existing -> syncProPackageItems(existing, List.of(qrCreate, qrMenu, qrAgent, qrAnalytics)))
                .orElseGet(() -> createProPackage(qrCreate, qrMenu, qrAgent, qrAnalytics));
    }

    private Product ensureProduct(ProductCode code, String name) {
        return productRepository.findByCode(code).orElseGet(() -> productRepository.save(
                Product.builder()
                        .code(code)
                        .name(name)
                        .description(name + " ürünü")
                        .active(true)
                        .build()
        ));
    }

    private PlanPackage createFreePackage(Product qrCreate) {
        PlanPackage planPackage = PlanPackage.builder()
                .code(PackageCode.FREE_PACKAGE)
                .name("Free")
                .description("5 adet QR oluşturma hakkı")
                .price(BigDecimal.ZERO)
                .currency("TRY")
                .active(true)
                .validityDays(FREE_VALIDITY_DAYS)
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

    private PlanPackage createProPackage(Product qrCreate, Product qrMenu, Product qrAgent, Product qrAnalytics) {
        PlanPackage planPackage = PlanPackage.builder()
                .code(PackageCode.PRO_PACKAGE)
                .name("Pro")
                .description("Sınırsız QR oluşturma, menü, agent ve detaylı raporlama")
                .price(PRO_PRICE)
                .currency("TRY")
                .active(true)
                .validityDays(PRO_VALIDITY_DAYS)
                .build();

        planPackage.setItems(buildUnlimitedItems(planPackage, List.of(qrCreate, qrMenu, qrAgent, qrAnalytics)));
        return planPackageRepository.save(planPackage);
    }

    private PlanPackage syncProPackageItems(PlanPackage planPackage, List<Product> requiredProducts) {
        planPackage.setName("Pro");
        planPackage.setDescription("Sınırsız QR oluşturma, menü, agent ve detaylı raporlama");
        planPackage.setPrice(PRO_PRICE);
        planPackage.setCurrency("TRY");
        planPackage.setActive(true);
        planPackage.setValidityDays(PRO_VALIDITY_DAYS);

        Set<ProductCode> existingCodes = planPackage.getItems().stream()
                .map(item -> item.getProduct().getCode())
                .collect(Collectors.toSet());

        if (!existingCodes.equals(PRO_PRODUCT_CODES)) {
            planPackage.getItems().clear();
            planPackage.getItems().addAll(buildUnlimitedItems(planPackage, requiredProducts));
        }

        return planPackageRepository.save(planPackage);
    }

    private List<PlanPackageItem> buildUnlimitedItems(PlanPackage planPackage, List<Product> products) {
        List<PlanPackageItem> items = new ArrayList<>();
        for (Product product : products) {
            items.add(PlanPackageItem.builder()
                    .planPackage(planPackage)
                    .product(product)
                    .quantity(0)
                    .unlimited(true)
                    .build());
        }
        return items;
    }
}
