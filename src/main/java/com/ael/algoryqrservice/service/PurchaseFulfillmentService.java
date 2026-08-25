package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.PlanPackageItem;
import com.ael.algoryqrservice.model.Product;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.PurchaseFulfillment;
import com.ael.algoryqrservice.model.dto.PaymentCompletedEventDto;
import com.ael.algoryqrservice.model.dto.PaymentEventMetadata;
import com.ael.algoryqrservice.model.dto.PurchaseFulfillmentResponse;
import com.ael.algoryqrservice.model.enums.FulfillmentStatus;
import com.ael.algoryqrservice.model.enums.PaymentStyle;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.model.enums.SubscriptionStatus;
import com.ael.algoryqrservice.repository.PlanPackageRepository;
import com.ael.algoryqrservice.repository.ProductRepository;
import com.ael.algoryqrservice.repository.PurchaseFulfillmentRepository;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.service.entitlement.PackageEntitlementWriter;
import com.ael.algoryqrservice.util.Enums;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PurchaseFulfillmentService {

    private final PurchaseFulfillmentRepository fulfillmentRepository;
    private final PurchaseRepository purchaseRepository;
    private final PlanPackageRepository planPackageRepository;
    private final ProductRepository productRepository;
    private final PackageEntitlementWriter entitlementWriter;
    private final PackageActivationService packageActivationService;
    private final MenuPublicAccessService menuPublicAccessService;
    private final FulfillmentGrantService fulfillmentGrantService;

    @Transactional
    public void initializeSchedule(Purchase purchase, String serviceName) {
        if (!fulfillmentRepository.findByPurchaseIdOrderByInstallmentNumberAsc(purchase.getId()).isEmpty()) {
            return;
        }
        LocalDateTime anchor = purchase.getPurchasedAt() != null ? purchase.getPurchasedAt() : LocalDateTime.now();
        String attemptKey = resolvePaymentAttemptKey(purchase);
        String installmentId = attemptKey + ":1";
        fulfillmentRepository.save(PurchaseFulfillment.builder()
                .purchaseId(purchase.getId())
                .installmentId(installmentId)
                .installmentNumber(1)
                .installmentCount(1)
                .status(FulfillmentStatus.PENDING)
                .dueAt(anchor)
                .amount(purchase.getPrice())
                .currency(purchase.getCurrency())
                .eventId("pending:" + attemptKey)
                .build());
    }

    private String resolvePaymentAttemptKey(Purchase purchase) {
        String conversationId = purchase.getPaymentConversationId();
        if (conversationId != null && !conversationId.isBlank()) {
            return conversationId;
        }
        return String.valueOf(purchase.getId());
    }

    @Transactional
    public void fulfillPaidInstallment(
            Purchase purchase,
            PlanPackage planPackage,
            PaymentCompletedEventDto event,
            PaymentEventMetadata metadata
    ) {
        PurchaseFulfillment fulfillment = resolveFulfillment(purchase, event, metadata);
        if (fulfillment.getStatus() == FulfillmentStatus.PAID) {
            return;
        }

        Duration paidPeriod = Duration.between(metadata.periodStart(), metadata.periodEnd());
        LocalDateTime now = LocalDateTime.now();
        boolean firstPaidInstallment = fulfillmentRepository
                .findByPurchaseIdAndStatusOrderByInstallmentNumberAsc(purchase.getId(), FulfillmentStatus.PAID)
                .isEmpty();

        LocalDateTime periodStart;
        LocalDateTime periodEnd;
        if (firstPaidInstallment && purchase.getPurchaseType() == PurchaseType.ADD_ON) {
            periodStart = now;
            periodEnd = purchase.getExpiresAt() != null && purchase.getExpiresAt().isAfter(now)
                    ? purchase.getExpiresAt()
                    : now.plus(paidPeriod);
        } else {
            periodStart = purchase.getExpiresAt() != null && purchase.getExpiresAt().isAfter(now)
                    ? purchase.getExpiresAt()
                    : now;
            periodEnd = periodStart.plus(paidPeriod);
        }

        fulfillment.setEventId(event.getEventId());
        fulfillment.setStatus(FulfillmentStatus.PAID);
        fulfillment.setStartsAt(periodStart);
        fulfillment.setExpiresAt(periodEnd);
        fulfillment.setFailureReason(null);
        fulfillmentRepository.save(fulfillment);

        purchase.setExpiresAt(periodEnd);
        purchase.setCurrentPeriodPaidAt(resolveOccurredAt(event));
        if (event.getConversationId() != null && !event.getConversationId().isBlank()) {
            purchase.setCurrentPeriodConversationId(event.getConversationId());
            if (purchase.getPaymentConversationId() == null || purchase.getPaymentConversationId().isBlank()) {
                purchase.setPaymentConversationId(event.getConversationId());
            }
        }
        if (firstPaidInstallment) {
            purchase.setStartsAt(periodStart);
            purchase.setStatus(PurchaseStatus.ACTIVE);
            if (purchase.getPurchaseType() == PurchaseType.ADD_ON) {
                grantAddonEntitlement(purchase);
                menuPublicAccessService.syncForUser(purchase.getUserId());
            } else {
                grantEntitlements(purchase, planPackage);
                packageActivationService.activatePurchasedPackage(purchase);
            }
        }
        purchase.setStatus(PurchaseStatus.ACTIVE);
        purchase.setPaymentId(event.getPaymentId());
        applyCardSnapshotFromEvent(purchase, event);
        if (event.getSubscriptionId() != null && !event.getSubscriptionId().isBlank()) {
            purchase.setSubscriptionId(event.getSubscriptionId());
        }
        if (purchase.getPaymentStyle() == PaymentStyle.SUBSCRIPTION) {
            purchase.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
            purchase.setSubscriptionGraceEndsAt(null);
            purchase.setCancelAtPeriodEnd(false);
        } else {
            Enums.parse(SubscriptionStatus.class, event.getSubscriptionStatus())
                    .ifPresent(purchase::setSubscriptionStatus);
        }
        purchase.setCancellationReason(null);
        purchaseRepository.save(purchase);
        entitlementWriter.synchronizePeriod(purchase);
        tryGrantFulfillment(purchase, planPackage);
        menuPublicAccessService.syncForUser(purchase.getUserId());
    }

    @Transactional
    public void recordUnpaidInstallment(
            Purchase purchase,
            PaymentCompletedEventDto event,
            PaymentEventMetadata metadata,
            FulfillmentStatus status
    ) {
        PurchaseFulfillment fulfillment = resolveFulfillment(purchase, event, metadata);
        if (fulfillment.getStatus() == FulfillmentStatus.PAID) {
            return;
        }
        fulfillment.setEventId(event.getEventId());
        fulfillment.setStatus(status);
        fulfillment.setFailureReason(event.getFailureReason());
        fulfillmentRepository.save(fulfillment);
        if (status == FulfillmentStatus.OVERDUE) {
            menuPublicAccessService.syncForUser(purchase.getUserId());
        }
    }

    @Transactional
    public void revokeInstallment(
            Purchase purchase,
            PaymentCompletedEventDto event,
            PaymentEventMetadata metadata
    ) {
        PurchaseFulfillment fulfillment = fulfillmentRepository
                .findByPurchaseIdAndInstallmentId(purchase.getId(), metadata.installmentId())
                .orElseGet(() -> newFulfillment(purchase, event, metadata));
        fulfillment.setEventId(event.getEventId());
        fulfillment.setStatus(FulfillmentStatus.REVOKED);
        fulfillment.setFailureReason(event.getFailureReason());
        fulfillmentRepository.save(fulfillment);
        recalculatePaidPeriod(purchase);
    }

    @Transactional(readOnly = true)
    public List<PurchaseFulfillmentResponse> getFulfillments(Long purchaseId) {
        return fulfillmentRepository.findByPurchaseIdOrderByInstallmentNumberAsc(purchaseId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void cancelOpenFulfillments(Long purchaseId) {
        List<PurchaseFulfillment> fulfillments = fulfillmentRepository
                .findByPurchaseIdOrderByInstallmentNumberAsc(purchaseId);
        boolean changed = false;
        for (PurchaseFulfillment fulfillment : fulfillments) {
            if (fulfillment.getStatus() == FulfillmentStatus.PENDING
                    || fulfillment.getStatus() == FulfillmentStatus.OVERDUE) {
                fulfillment.setStatus(FulfillmentStatus.REVOKED);
                changed = true;
            }
        }
        if (changed) {
            fulfillmentRepository.saveAll(fulfillments);
        }
    }

    @Transactional(readOnly = true)
    public LocalDateTime findNextPaymentDueAt(Long purchaseId) {
        return fulfillmentRepository.findByPurchaseIdOrderByInstallmentNumberAsc(purchaseId).stream()
                .filter(fulfillment -> fulfillment.getStatus() == FulfillmentStatus.PENDING
                        || fulfillment.getStatus() == FulfillmentStatus.OVERDUE)
                .map(PurchaseFulfillment::getDueAt)
                .filter(dueAt -> dueAt != null)
                .min(LocalDateTime::compareTo)
                .orElse(null);
    }

    private PurchaseFulfillment resolveFulfillment(
            Purchase purchase,
            PaymentCompletedEventDto event,
            PaymentEventMetadata metadata
    ) {
        if (purchase.getPaymentStyle() == PaymentStyle.SUBSCRIPTION) {
            Optional<PurchaseFulfillment> byNumber = fulfillmentRepository
                    .findByPurchaseIdAndInstallmentNumber(purchase.getId(), metadata.installmentNumber());
            if (byNumber.isPresent()) {
                return byNumber.get();
            }
            return fulfillmentRepository.save(PurchaseFulfillment.builder()
                    .purchaseId(purchase.getId())
                    .installmentId(metadata.installmentId())
                    .installmentNumber(metadata.installmentNumber())
                    .installmentCount(metadata.installmentNumber())
                    .status(FulfillmentStatus.PENDING)
                    .dueAt(metadata.periodStart())
                    .amount(purchase.getPrice())
                    .currency(purchase.getCurrency())
                    .eventId("pending:" + metadata.installmentId())
                    .build());
        }
        return fulfillmentRepository
                .findByPurchaseIdAndInstallmentId(purchase.getId(), metadata.installmentId())
                .orElseGet(() -> newFulfillment(purchase, event, metadata));
    }

    private PurchaseFulfillment newFulfillment(
            Purchase purchase,
            PaymentCompletedEventDto event,
            PaymentEventMetadata metadata
    ) {
        return PurchaseFulfillment.builder()
                .purchaseId(purchase.getId())
                .installmentId(metadata.installmentId())
                .installmentNumber(metadata.installmentNumber())
                .installmentCount(metadata.installmentCount())
                .eventId(event.getEventId())
                .status(FulfillmentStatus.FAILED)
                .dueAt(metadata.periodStart())
                .amount(event.getAmount())
                .currency(event.getCurrency())
                .build();
    }

    private void tryGrantFulfillment(Purchase purchase, PlanPackage planPackage) {
        try {
            if (purchase.getPurchaseType() == PurchaseType.ADD_ON) {
                fulfillmentGrantService.grantAddonFulfillment(purchase);
            } else if (planPackage != null) {
                fulfillmentGrantService.grantPackageFulfillment(purchase, planPackage);
            }
        } catch (Exception e) {
            log.error("Fulfillment grant failed for purchaseId={}: {}", purchase.getId(), e.getMessage(), e);
        }
    }

    private void grantAddonEntitlement(Purchase purchase) {
        Product product = productRepository.findByCode(purchase.getPackageCode())
                .orElseThrow(() -> new IllegalStateException("Urun bulunamadi: " + purchase.getPackageCode()));
        int quantity = purchase.getInstallmentCount() == null || purchase.getInstallmentCount() < 1
                ? 1
                : purchase.getInstallmentCount();
        entitlementWriter.grant(purchase, product.getId(), product.getCode(), quantity, false);
    }

    private void grantEntitlements(Purchase purchase, PlanPackage planPackage) {
        PlanPackage packageWithItems = planPackageRepository.findByIdWithItems(planPackage.getId())
                .orElseThrow(() -> new IllegalStateException("Paket bulunamadı: " + planPackage.getId()));
        for (PlanPackageItem item : packageWithItems.getItems()) {
            entitlementWriter.grant(
                    purchase,
                    item.getProduct().getId(),
                    item.getProduct().getCode(),
                    item.getQuantity(),
                    item.isUnlimited()
            );
        }
    }

    private void recalculatePaidPeriod(Purchase purchase) {
        if (purchase.getStatus() == PurchaseStatus.CANCELLED) {
            return;
        }
        List<PurchaseFulfillment> paid = fulfillmentRepository
                .findByPurchaseIdAndStatusOrderByInstallmentNumberAsc(purchase.getId(), FulfillmentStatus.PAID);
        if (paid.isEmpty()) {
            purchase.setStatus(PurchaseStatus.EXPIRED);
            purchase.setExpiresAt(LocalDateTime.now());
            purchaseRepository.save(purchase);
            entitlementWriter.synchronizePeriod(purchase);
            packageActivationService.ensureSubscriptionState(purchase.getUserId());
            menuPublicAccessService.syncForUser(purchase.getUserId());
            return;
        }

        LocalDateTime startsAt = purchase.getStartsAt() == null
                ? LocalDateTime.now()
                : purchase.getStartsAt();
        Duration totalDuration = paid.stream()
                .map(item -> Duration.between(item.getStartsAt(), item.getExpiresAt()))
                .reduce(Duration.ZERO, Duration::plus);
        purchase.setStartsAt(startsAt);
        purchase.setExpiresAt(startsAt.plus(totalDuration));
        purchase.setStatus(purchase.getExpiresAt().isAfter(LocalDateTime.now())
                ? PurchaseStatus.ACTIVE
                : PurchaseStatus.EXPIRED);
        purchaseRepository.save(purchase);
        entitlementWriter.synchronizePeriod(purchase);
        if (purchase.getStatus() == PurchaseStatus.EXPIRED) {
            packageActivationService.ensureSubscriptionState(purchase.getUserId());
        }
        menuPublicAccessService.syncForUser(purchase.getUserId());
    }

    private void applyCardSnapshotFromEvent(Purchase purchase, PaymentCompletedEventDto event) {
        if (event.getSourceMetadata() == null || event.getSourceMetadata().isEmpty()) {
            return;
        }
        String brand = firstMetadataString(event, "cardBrand", "brand", "cardAssociation");
        String lastFour = firstMetadataString(event, "cardLastFour", "lastFour", "last4", "lastFourDigits");
        if (brand != null) {
            purchase.setCardBrand(brand);
        }
        if (lastFour != null) {
            purchase.setCardLastFour(lastFour);
        }
    }

    private String firstMetadataString(PaymentCompletedEventDto event, String... keys) {
        for (String key : keys) {
            Object value = event.getSourceMetadata().get(key);
            if (value == null) {
                continue;
            }
            String text = String.valueOf(value).trim();
            if (!text.isEmpty() && !"null".equalsIgnoreCase(text)) {
                return text;
            }
        }
        return null;
    }

    private LocalDateTime resolveOccurredAt(PaymentCompletedEventDto event) {
        if (event.getOccurredAt() == null || event.getOccurredAt().isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(event.getOccurredAt());
        } catch (RuntimeException ignored) {
            try {
                return java.time.OffsetDateTime.parse(event.getOccurredAt()).toLocalDateTime();
            } catch (RuntimeException ignoredAgain) {
                try {
                    return java.time.Instant.parse(event.getOccurredAt())
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDateTime();
                } catch (RuntimeException ignoredThird) {
                    return LocalDateTime.now();
                }
            }
        }
    }

    private PurchaseFulfillmentResponse toResponse(PurchaseFulfillment fulfillment) {
        return PurchaseFulfillmentResponse.builder()
                .id(fulfillment.getId())
                .installmentId(fulfillment.getInstallmentId())
                .installmentNumber(fulfillment.getInstallmentNumber())
                .installmentCount(fulfillment.getInstallmentCount())
                .status(fulfillment.getStatus())
                .startsAt(fulfillment.getStartsAt())
                .expiresAt(fulfillment.getExpiresAt())
                .dueAt(fulfillment.getDueAt())
                .amount(fulfillment.getAmount())
                .currency(fulfillment.getCurrency())
                .failureReason(fulfillment.getFailureReason())
                .build();
    }
}
