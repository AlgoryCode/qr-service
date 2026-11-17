package com.ael.algoryqrservice.qrservice.billing.service;

import com.ael.algoryqrservice.qrservice.exception.EntitlementDeniedException.Upgrade;
import com.ael.algoryqrservice.qrservice.model.PlanPackage;
import com.ael.algoryqrservice.qrservice.model.PlanPackageItem;
import com.ael.algoryqrservice.qrservice.model.Product;
import com.ael.algoryqrservice.qrservice.product.repository.ProductRepository;
import com.ael.algoryqrservice.qrservice.repository.PlanPackageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** What the merchant could buy to get past a refused feature: bigger packages first, then add-ons. */
@Service
@RequiredArgsConstructor
public class UpgradeOptionService {

    private final PlanPackageRepository planPackageRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public List<Upgrade> forFeature(String feature, Long currentLimit, String currentPackageCode) {
        if (feature == null || feature.isBlank()) {
            return List.of();
        }
        List<Upgrade> options = new ArrayList<>();
        List<PlanPackage> packages = planPackageRepository.findAllByIdWithItems(
                planPackageRepository.findByActiveTrueOrderByPriceAsc().stream().map(PlanPackage::getId).toList());
        packages.stream()
                .filter(pkg -> pkg.isPurchasable() && !pkg.isSystemManaged())
                .filter(pkg -> !Objects.equals(pkg.getCode(), currentPackageCode))
                .sorted(Comparator.comparing(PlanPackage::getPrice, Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(pkg -> grant(pkg, feature)
                        .filter(item -> item.isUnlimited() || currentLimit == null
                                || (item.getQuantity() != null && item.getQuantity() > currentLimit))
                        .ifPresent(item -> options.add(new Upgrade(
                                "PACKAGE", pkg.getCode(), pkg.getName(), item.getQuantity(), item.isUnlimited()))));
        for (Product addon : productRepository.findByActiveTrueAndAddonPurchasableTrueOrderByNameAsc()) {
            if (feature.equals(featureOf(addon))) {
                options.add(new Upgrade("ADDON", addon.getCode(), addon.getName(), 1, false));
            }
        }
        return options;
    }

    private static Optional<PlanPackageItem> grant(PlanPackage pkg, String feature) {
        return pkg.getItems().stream()
                .filter(item -> item.getProduct() != null && feature.equals(featureOf(item.getProduct())))
                .findFirst();
    }

    private static String featureOf(Product product) {
        return product.getFeatureCode() == null || product.getFeatureCode().isBlank()
                ? product.getCode()
                : product.getFeatureCode();
    }
}
