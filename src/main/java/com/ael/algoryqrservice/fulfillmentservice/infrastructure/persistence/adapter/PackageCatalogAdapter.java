package com.ael.algoryqrservice.fulfillmentservice.infrastructure.persistence.adapter;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.PackageKind;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.PlanPackage;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.PlanPackageItem;
import com.ael.algoryqrservice.fulfillmentservice.domain.port.PackageCatalogPort;
import com.ael.algoryqrservice.fulfillmentservice.infrastructure.persistence.entity.PlanPackageEntity;
import com.ael.algoryqrservice.fulfillmentservice.infrastructure.persistence.entity.PlanPackageItemEntity;
import com.ael.algoryqrservice.fulfillmentservice.infrastructure.persistence.repository.PlanPackageJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class PackageCatalogAdapter implements PackageCatalogPort {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final PlanPackageJpaRepository planPackageJpaRepository;
    private final JsonMapper jsonMapper;

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

    private PlanPackage toDomain(PlanPackageEntity entity) {
        return new PlanPackage(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getDescription(),
                PackageKind.CATALOG,
                parseFeatures(entity.getFeaturesJson()),
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
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private PlanPackage toDomainWithoutItems(PlanPackageEntity entity) {
        return new PlanPackage(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getDescription(),
                PackageKind.CATALOG,
                parseFeatures(entity.getFeaturesJson()),
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
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private PlanPackageItem toItem(PlanPackageItemEntity item) {
        return new PlanPackageItem(
                item.getId(),
                item.getProduct().getId(),
                item.getProduct().getCode(),
                item.getProduct().getFeatureCode(),
                item.getProduct().getScopeCode(),
                item.getProduct().getTypeId(),
                item.getQuantity(),
                item.isUnlimited()
        );
    }

    private List<String> parseFeatures(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return jsonMapper.readValue(json, STRING_LIST);
        } catch (Exception exception) {
            log.warn("Failed to parse package features json");
            return Collections.emptyList();
        }
    }
}
