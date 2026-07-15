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
import java.util.List;

@Service
@RequiredArgsConstructor
public class PackageCatalogService {

    private static final int FREE_QR_CREATE_QUANTITY = 5;
    private static final int FREE_VALIDITY_DAYS = 36_500;

    private final ProductRepository productRepository;
    private final PlanPackageRepository planPackageRepository;

    @Transactional
    public PlanPackage ensureFreePackage() {
        Product qrCreate = ensureProduct(ProductCode.QR_CREATE, "QR Oluşturma");
        ensureProduct(ProductCode.QR_MENU, "QR Menü");

        return planPackageRepository.findByCode(PackageCode.FREE_PACKAGE)
                .orElseGet(() -> createFreePackage(qrCreate));
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
}
