package com.ael.algoryqrservice.fulfillmentservice.infrastructure.persistence.adapter;

import com.ael.algoryqrservice.fulfillmentservice.domain.model.PackageStatus;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.UserEntitlement;
import com.ael.algoryqrservice.fulfillmentservice.domain.port.EntitlementPort;
import com.ael.algoryqrservice.fulfillmentservice.infrastructure.persistence.entity.UserEntitlementEntity;
import com.ael.algoryqrservice.fulfillmentservice.infrastructure.persistence.repository.UserEntitlementJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class EntitlementAdapter implements EntitlementPort {

    private final UserEntitlementJpaRepository userEntitlementJpaRepository;

    @Override
    public List<UserEntitlement> saveAll(List<UserEntitlement> entitlements) {
        return userEntitlementJpaRepository.saveAll(entitlements.stream().map(this::toEntity).toList())
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public UserEntitlement save(UserEntitlement entitlement) {
        return toDomain(userEntitlementJpaRepository.save(toEntity(entitlement)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserEntitlement> findActiveByUserId(Long userId) {
        return userEntitlementJpaRepository.findActiveByUserId(userId, PackageStatus.ACTIVE, Instant.now())
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void supersedeOtherEntitlements(Long userId, Long keepFulfillmentId) {
        userEntitlementJpaRepository.supersedeOthers(
                userId,
                keepFulfillmentId,
                PackageStatus.ACTIVE,
                PackageStatus.SUPERSEDED,
                Instant.now()
        );
    }

    private UserEntitlement toDomain(UserEntitlementEntity entity) {
        return new UserEntitlement(
                entity.getId(),
                entity.getUserId(),
                entity.getFulfillmentId(),
                entity.getPurchaseId(),
                entity.getProductId(),
                entity.getProductTypeId(),
                entity.getFeatureCode(),
                entity.getScopeCode(),
                entity.getQuantity(),
                entity.isUnlimited(),
                entity.getUsedQuantity(),
                entity.getSource(),
                entity.getStatus(),
                entity.getStartsAt(),
                entity.getExpiresAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private UserEntitlementEntity toEntity(UserEntitlement entitlement) {
        UserEntitlementEntity entity = new UserEntitlementEntity();
        entity.setId(entitlement.id());
        entity.setUserId(entitlement.userId());
        entity.setFulfillmentId(entitlement.fulfillmentId());
        entity.setPurchaseId(entitlement.purchaseId());
        entity.setProductId(entitlement.productId());
        entity.setProductTypeId(entitlement.productTypeId());
        entity.setFeatureCode(entitlement.featureCode());
        entity.setScopeCode(entitlement.scopeCode());
        entity.setQuantity(entitlement.quantity());
        entity.setUnlimited(entitlement.unlimited());
        entity.setUsedQuantity(entitlement.usedQuantity());
        entity.setSource(entitlement.source());
        entity.setStatus(entitlement.status());
        entity.setStartsAt(entitlement.startsAt());
        entity.setExpiresAt(entitlement.expiresAt());
        entity.setCreatedAt(entitlement.createdAt());
        entity.setUpdatedAt(entitlement.updatedAt());
        return entity;
    }
}
