package com.ael.algoryqrservice.fulfillmentservice.infrastructure.persistence.adapter;

import com.ael.algoryqrservice.fulfillmentservice.domain.model.ActivePackage;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.FulfillmentStatus;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.PackageFulfillment;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.PackageStatus;
import com.ael.algoryqrservice.fulfillmentservice.domain.port.FulfillmentRepositoryPort;
import com.ael.algoryqrservice.fulfillmentservice.infrastructure.persistence.entity.ActivePackageEntity;
import com.ael.algoryqrservice.fulfillmentservice.infrastructure.persistence.entity.PackageFulfillmentEntity;
import com.ael.algoryqrservice.fulfillmentservice.infrastructure.persistence.repository.ActivePackageJpaRepository;
import com.ael.algoryqrservice.fulfillmentservice.infrastructure.persistence.repository.PackageFulfillmentJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class FulfillmentRepositoryAdapter implements FulfillmentRepositoryPort {

    private final PackageFulfillmentJpaRepository packageFulfillmentJpaRepository;
    private final ActivePackageJpaRepository activePackageJpaRepository;

    @Override
    public Optional<PackageFulfillment> findByEventId(String eventId) {
        return packageFulfillmentJpaRepository.findByEventId(eventId).map(this::toDomain);
    }

    @Override
    public Optional<PackageFulfillment> findById(Long id) {
        return packageFulfillmentJpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public PackageFulfillment saveFulfillment(PackageFulfillment fulfillment) {
        return toDomain(packageFulfillmentJpaRepository.save(toEntity(fulfillment)));
    }

    @Override
    public void supersedeOtherFulfillments(Long userId, Long keepFulfillmentId) {
        packageFulfillmentJpaRepository.supersedeOthers(
                userId,
                keepFulfillmentId,
                FulfillmentStatus.SUPERSEDED,
                FulfillmentStatus.ACTIVE
        );
    }

    @Override
    public Optional<ActivePackage> findActiveByUserId(Long userId) {
        return activePackageJpaRepository.findByUserId(userId).map(this::toDomain);
    }

    @Override
    public ActivePackage upsertActivePackage(ActivePackage activePackage) {
        ActivePackageEntity entity = activePackageJpaRepository.findByUserId(activePackage.userId())
                .orElseGet(ActivePackageEntity::new);
        Instant now = Instant.now();
        entity.setUserId(activePackage.userId());
        entity.setFulfillmentId(activePackage.fulfillmentId());
        entity.setPurchaseId(activePackage.purchaseId());
        entity.setPackageId(activePackage.packageId());
        entity.setPackageCode(activePackage.packageCode());
        entity.setPeriodStart(activePackage.periodStart());
        entity.setPeriodEnd(activePackage.periodEnd());
        entity.setStatus(activePackage.status() == null ? PackageStatus.ACTIVE : activePackage.status());
        entity.setActivatedAt(now);
        entity.setUpdatedAt(now);
        return toDomain(activePackageJpaRepository.save(entity));
    }

    @Override
    public Page<PackageFulfillment> findByUserId(Long userId, Pageable pageable) {
        return packageFulfillmentJpaRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::toDomain);
    }

    private PackageFulfillment toDomain(PackageFulfillmentEntity entity) {
        return new PackageFulfillment(
                entity.getId(),
                entity.getEventId(),
                entity.getEventType(),
                entity.getPurchaseId(),
                entity.getUserId(),
                entity.getPackageId(),
                entity.getPackageCode(),
                entity.getConversationId(),
                entity.getPaymentId(),
                entity.getSubscriptionId(),
                entity.getBillingCycleNumber(),
                entity.getPeriodStart(),
                entity.getPeriodEnd(),
                entity.getAmount(),
                entity.getCurrency(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private PackageFulfillmentEntity toEntity(PackageFulfillment fulfillment) {
        PackageFulfillmentEntity entity = new PackageFulfillmentEntity();
        entity.setId(fulfillment.id());
        entity.setEventId(fulfillment.eventId());
        entity.setEventType(fulfillment.eventType());
        entity.setPurchaseId(fulfillment.purchaseId());
        entity.setUserId(fulfillment.userId());
        entity.setPackageId(fulfillment.packageId());
        entity.setPackageCode(fulfillment.packageCode());
        entity.setConversationId(fulfillment.conversationId());
        entity.setPaymentId(fulfillment.paymentId());
        entity.setSubscriptionId(fulfillment.subscriptionId());
        entity.setBillingCycleNumber(fulfillment.billingCycleNumber());
        entity.setPeriodStart(fulfillment.periodStart());
        entity.setPeriodEnd(fulfillment.periodEnd());
        entity.setAmount(fulfillment.amount());
        entity.setCurrency(fulfillment.currency());
        entity.setStatus(fulfillment.status());
        entity.setCreatedAt(fulfillment.createdAt());
        entity.setUpdatedAt(fulfillment.updatedAt());
        return entity;
    }

    private ActivePackage toDomain(ActivePackageEntity entity) {
        return new ActivePackage(
                entity.getId(),
                entity.getUserId(),
                entity.getFulfillmentId(),
                entity.getPurchaseId(),
                entity.getPackageId(),
                entity.getPackageCode(),
                entity.getPeriodStart(),
                entity.getPeriodEnd(),
                entity.getStatus(),
                entity.getActivatedAt(),
                entity.getUpdatedAt()
        );
    }
}
