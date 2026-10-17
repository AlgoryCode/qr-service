package com.ael.algoryqrservice.fulfillmentservice.infrastructure.persistence.adapter;

import com.ael.algoryqrservice.fulfillmentservice.domain.model.PackageKind;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.PlanPackage;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.PlanPackageItem;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.ProductType;
import com.ael.algoryqrservice.fulfillmentservice.domain.port.PackageCatalogPort;
import com.ael.algoryqrservice.fulfillmentservice.infrastructure.persistence.repository.PlanPackageJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PackageCatalogAdapter implements PackageCatalogPort {

    private final PlanPackageJpaRepository planPackageJpaRepository;

    @Override
    public Optional<PlanPackage> findByCode(String code) {
        return planPackageJpaRepository.findByCodeWithItems(code).map(this::toDomain);
    }

    @Override
    public Optional<PlanPackage> findById(Long id) {
        return planPackageJpaRepository.findByIdWithItems(id).map(this::toDomain);
    }

    @Override
    public Page<PlanPackage> findActive(Pageable pageable) {
        return planPackageJpaRepository.findByActiveTrue(pageable).map(this::toDomainWithoutItems);
    }

    @Override
    public Page<PlanPackage> findPurchasable(Pageable pageable) {
        return planPackageJpaRepository.findByActiveTrueAndPurchasableTrue(pageable).map(this::toDomainWithoutItems);
    }

    private PlanPackage toDomain(com.ael.algoryqrservice.qrservice.model.PlanPackage entity) {
        return new PlanPackage(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getDescription(),
                PackageKind.CATALOG,
                featuresOf(entity),
                entity.getPrice(),
                entity.getYearlyPrice(),
                entity.getSubtotal(),
                entity.getVatAmount(),
                entity.getCurrency(),
                entity.isActive(),
                entity.getValidityDays(),
                entity.getPriority(),
                entity.isPurchasable(),
                entity.isSystemManaged(),
                entity.getItems().stream().map(this::toItem).toList(),
                toInstant(entity.getCreatedAt()),
                toInstant(entity.getUpdatedAt())
        );
    }

    private PlanPackage toDomainWithoutItems(com.ael.algoryqrservice.qrservice.model.PlanPackage entity) {
        return new PlanPackage(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getDescription(),
                PackageKind.CATALOG,
                featuresOf(entity),
                entity.getPrice(),
                entity.getYearlyPrice(),
                entity.getSubtotal(),
                entity.getVatAmount(),
                entity.getCurrency(),
                entity.isActive(),
                entity.getValidityDays(),
                entity.getPriority(),
                entity.isPurchasable(),
                entity.isSystemManaged(),
                List.of(),
                toInstant(entity.getCreatedAt()),
                toInstant(entity.getUpdatedAt())
        );
    }

    private PlanPackageItem toItem(com.ael.algoryqrservice.qrservice.model.PlanPackageItem item) {
        var product = item.getProduct();
        return new PlanPackageItem(
                item.getId(),
                product.getId(),
                product.getCode(),
                product.getFeatureCode(),
                product.getScopeCode(),
                toProductType(product.getTypeId()),
                item.getQuantity(),
                item.isUnlimited()
        );
    }

    private List<String> featuresOf(com.ael.algoryqrservice.qrservice.model.PlanPackage entity) {
        if (entity.getFeatures() == null || entity.getFeatures().isEmpty()) {
            return Collections.emptyList();
        }
        return entity.getFeatures();
    }

    private ProductType toProductType(com.ael.algoryqrservice.qrservice.model.enums.ProductType typeId) {
        if (typeId == null) {
            return null;
        }
        return ProductType.valueOf(typeId.name());
    }

    private Instant toInstant(java.time.LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.toInstant(ZoneOffset.UTC);
    }
}
