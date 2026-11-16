package com.ael.algoryqrservice.fulfillmentservice.infrastructure.persistence.adapter;

import com.ael.algoryqrservice.fulfillmentservice.domain.model.ActivePackage;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.FulfillmentStatus;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.PackageFulfillment;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.PackageStatus;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.SubscriptionKind;
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
import org.springframework.data.domain.PageRequest;
import java.util.List;
import java.time.LocalDate;
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
    public void supersedeOtherFulfillments(Long merchantId, Long keepFulfillmentId) {
        packageFulfillmentJpaRepository.supersedeOthers(
                merchantId,
                keepFulfillmentId,
                FulfillmentStatus.SUPERSEDED,
                FulfillmentStatus.ACTIVE,
                Instant.now()
        );
    }

    @Override
    public Optional<ActivePackage> findActiveByMerchantId(Long merchantId) {
        return activePackageJpaRepository.findByMerchantId(merchantId).map(this::toDomain);
    }

    @Override
    public ActivePackage upsertActivePackage(ActivePackage activePackage) {
        ActivePackageEntity entity = activePackageJpaRepository.findByMerchantId(activePackage.merchantId())
                .orElseGet(ActivePackageEntity::new);
        Instant now = Instant.now();
        entity.setMerchantId(activePackage.merchantId());
        entity.setFulfillmentId(activePackage.fulfillmentId());
        entity.setPurchaseId(activePackage.purchaseId());
        entity.setPackageId(activePackage.packageId());
        entity.setPackageCode(activePackage.packageCode());
        entity.setPeriodStart(activePackage.periodStart());
        entity.setPeriodEnd(activePackage.periodEnd());
        entity.setStatus(activePackage.status() == null ? PackageStatus.ACTIVE : activePackage.status());
        entity.setKind(activePackage.kind() == null ? SubscriptionKind.PAID : activePackage.kind());
        entity.setGraceEndsAt(activePackage.graceEndsAt());
        entity.setActivatedAt(now);
        entity.setUpdatedAt(now);
        return toDomain(activePackageJpaRepository.save(entity));
    }

    @Override
    public ActivePackage saveActivePackage(ActivePackage activePackage) {
        ActivePackageEntity entity = activePackageJpaRepository.findByMerchantId(activePackage.merchantId())
                .orElseThrow(() -> new IllegalStateException(
                        "No active package row for merchant " + activePackage.merchantId()));
        entity.setStatus(activePackage.status());
        entity.setGraceEndsAt(activePackage.graceEndsAt());
        entity.setUpdatedAt(Instant.now());
        return toDomain(activePackageJpaRepository.save(entity));
    }

    @Override
    public List<ActivePackage> findLifecycleDue(LocalDate today, Instant now, int limit) {
        return activePackageJpaRepository.findLifecycleDue(
                        PackageStatus.ACTIVE,
                        PackageStatus.GRACE_PERIOD,
                        SubscriptionKind.FREE,
                        today,
                        now,
                        PageRequest.of(0, Math.max(1, limit)))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<ActivePackage> findTrialsEndingBetween(LocalDate from, LocalDate to) {
        return activePackageJpaRepository.findTrialsEndingBetween(
                        SubscriptionKind.TRIAL, PackageStatus.ACTIVE, from, to)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public boolean hasAnyFulfillment(Long merchantId) {
        return packageFulfillmentJpaRepository.existsByMerchantId(merchantId);
    }

    @Override
    public Page<PackageFulfillment> findByMerchantId(Long merchantId, Pageable pageable) {
        return packageFulfillmentJpaRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId, pageable)
                .map(this::toDomain);
    }

    private PackageFulfillment toDomain(PackageFulfillmentEntity entity) {
        return new PackageFulfillment(
                entity.getId(),
                entity.getEventId(),
                entity.getEventType(),
                entity.getPurchaseId(),
                entity.getMerchantId(),
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
        entity.setMerchantId(fulfillment.merchantId());
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
                entity.getMerchantId(),
                entity.getFulfillmentId(),
                entity.getPurchaseId(),
                entity.getPackageId(),
                entity.getPackageCode(),
                entity.getPeriodStart(),
                entity.getPeriodEnd(),
                entity.getStatus(),
                entity.getActivatedAt(),
                entity.getUpdatedAt(),
                entity.getKind() == null ? SubscriptionKind.PAID : entity.getKind(),
                entity.getGraceEndsAt()
        );
    }
}
