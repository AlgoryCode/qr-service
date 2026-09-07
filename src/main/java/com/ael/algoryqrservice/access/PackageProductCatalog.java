package com.ael.algoryqrservice.access;

import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.repository.PlanPackageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PackageProductCatalog {

    private final PlanPackageRepository planPackageRepository;

    public boolean containsProduct(String packageCode, String productCode) {
        if (packageCode == null || packageCode.isBlank() || productCode == null || productCode.isBlank()) {
            return false;
        }
        PlanPackage planPackage = planPackageRepository.findByCodeWithItems(packageCode).orElse(null);
        if (planPackage == null || planPackage.getItems() == null) {
            return false;
        }
        return planPackage.getItems().stream()
                .filter(item -> item.getProduct() != null)
                .anyMatch(item -> productCode.equals(item.getProduct().getCode()));
    }
}
