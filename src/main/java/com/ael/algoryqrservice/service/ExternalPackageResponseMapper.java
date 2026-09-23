package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.access.AccessSession;
import com.ael.algoryqrservice.client.dto.ExternalActivePackageResponse;
import com.ael.algoryqrservice.client.dto.ExternalEntitlementResponse;
import com.ael.algoryqrservice.model.dto.FulfillmentDetailResponse;
import com.ael.algoryqrservice.model.dto.PurchaseResponse;
import com.ael.algoryqrservice.model.dto.PurchaseSummaryResponse;
import com.ael.algoryqrservice.model.dto.UserEntitlementResponse;
import com.ael.algoryqrservice.model.enums.AccessDecision;
import com.ael.algoryqrservice.model.enums.FulfillmentDetailSource;
import com.ael.algoryqrservice.model.enums.ProductType;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.util.AppTime;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class ExternalPackageResponseMapper {

    private static final String ACTIVE = "ACTIVE";
    private static final int EXPIRY_APPROACHING_DAYS = 7;

    public PurchaseResponse toPurchase(ExternalActivePackageResponse active) {
        LocalDateTime expiresAt = endOfDay(active.periodEnd());
        boolean usable = isOpen(active.status(), active.periodEnd());
        return PurchaseResponse.builder()
                .id(purchaseKey(active))
                .userId(active.userId())
                .packageId(active.packageId())
                .packageCode(active.packageCode())
                .packageName(active.packageName())
                .status(purchaseStatus(active.status(), usable))
                .purchaseType(PurchaseType.PAID)
                .startsAt(startOfDay(active.periodStart()))
                .expiresAt(expiresAt)
                .purchasedAt(toLocal(active.activatedAt()))
                .daysUntilExpiry(daysUntil(active.periodEnd()))
                .expiryApproaching(approaching(active.periodEnd()))
                .expired(!usable)
                .usable(usable)
                .build();
    }

    public PurchaseSummaryResponse toSummary(ExternalActivePackageResponse active) {
        PurchaseResponse purchase = toPurchase(active);
        return PurchaseSummaryResponse.builder()
                .purchaseId(purchase.getId())
                .userId(purchase.getUserId())
                .packageId(purchase.getPackageId())
                .packageCode(purchase.getPackageCode())
                .packageName(purchase.getPackageName())
                .status(purchase.getStatus())
                .purchaseType(purchase.getPurchaseType())
                .startsAt(purchase.getStartsAt())
                .expiresAt(purchase.getExpiresAt())
                .purchasedAt(purchase.getPurchasedAt())
                .daysUntilExpiry(purchase.getDaysUntilExpiry())
                .expiryApproaching(purchase.isExpiryApproaching())
                .expired(purchase.isExpired())
                .usable(purchase.isUsable())
                .build();
    }

    public AccessSession toAllowSession(ExternalActivePackageResponse active) {
        return AccessSession.of(
                AccessDecision.ALLOW,
                active.packageCode(),
                endOfDay(active.periodEnd()),
                null
        );
    }

    public List<UserEntitlementResponse> toEntitlements(
            List<ExternalEntitlementResponse> entitlements,
            Long activePurchaseId
    ) {
        return entitlements.stream()
                .map(entitlement -> toEntitlement(entitlement, activePurchaseId))
                .toList();
    }

    public List<FulfillmentDetailResponse> toDetails(List<ExternalEntitlementResponse> entitlements) {
        return entitlements.stream().map(this::toDetail).toList();
    }

    public Long purchaseKey(ExternalActivePackageResponse active) {
        if (active.purchaseId() != null) {
            return active.purchaseId();
        }
        if (active.fulfillmentId() != null) {
            return active.fulfillmentId();
        }
        return active.id();
    }

    private UserEntitlementResponse toEntitlement(ExternalEntitlementResponse entitlement, Long activePurchaseId) {
        int total = value(entitlement.quantity());
        int used = value(entitlement.usedQuantity());
        boolean unlimited = entitlement.unlimited();
        boolean usable = isEntitlementOpen(entitlement);
        String productCode = productCode(entitlement);
        return UserEntitlementResponse.builder()
                .id(entitlement.id())
                .productId(entitlement.productId())
                .productCode(productCode)
                .productName(productCode)
                .purchaseId(entitlement.purchaseId() != null ? entitlement.purchaseId() : activePurchaseId)
                .totalQuantity(total)
                .usedQuantity(used)
                .remainingQuantity(unlimited ? total : Math.max(0, total - used))
                .unlimited(unlimited)
                .startsAt(toLocal(entitlement.startsAt()))
                .expiresAt(toLocal(entitlement.expiresAt()))
                .purchaseStatus(PurchaseStatus.ACTIVE)
                .expired(!usable)
                .usable(usable)
                .build();
    }

    private FulfillmentDetailResponse toDetail(ExternalEntitlementResponse entitlement) {
        int total = value(entitlement.quantity());
        int used = value(entitlement.usedQuantity());
        return FulfillmentDetailResponse.builder()
                .id(entitlement.id())
                .fulfillmentId(entitlement.fulfillmentId())
                .featureCode(entitlement.featureCode())
                .scopeCode(entitlement.scopeCode())
                .productTypeId(enumValue(ProductType.class, entitlement.productTypeId()))
                .source(enumValue(FulfillmentDetailSource.class, entitlement.source()))
                .quantity(total)
                .unlimited(entitlement.unlimited())
                .usedQuantity(used)
                .remainingQuantity(entitlement.unlimited() ? total : Math.max(0, total - used))
                .startsAt(toLocal(entitlement.startsAt()))
                .expiresAt(toLocal(entitlement.expiresAt()))
                .build();
    }

    private boolean isEntitlementOpen(ExternalEntitlementResponse entitlement) {
        if (entitlement.status() != null && !ACTIVE.equals(entitlement.status())) {
            return false;
        }
        Instant expiresAt = entitlement.expiresAt();
        return expiresAt == null || expiresAt.isAfter(Instant.now());
    }

    private boolean isOpen(String status, LocalDate periodEnd) {
        if (!ACTIVE.equals(status)) {
            return false;
        }
        return periodEnd == null || !periodEnd.isBefore(LocalDate.now(AppTime.ZONE));
    }

    private PurchaseStatus purchaseStatus(String status, boolean usable) {
        if (usable) {
            return PurchaseStatus.ACTIVE;
        }
        PurchaseStatus parsed = enumValue(PurchaseStatus.class, status);
        return parsed == null ? PurchaseStatus.EXPIRED : parsed;
    }

    private boolean approaching(LocalDate periodEnd) {
        Integer days = daysUntil(periodEnd);
        return days != null && days > 0 && days <= EXPIRY_APPROACHING_DAYS;
    }

    private Integer daysUntil(LocalDate periodEnd) {
        if (periodEnd == null) {
            return null;
        }
        long days = ChronoUnit.DAYS.between(LocalDate.now(AppTime.ZONE), periodEnd);
        return (int) Math.max(days, 0);
    }

    private String productCode(ExternalEntitlementResponse entitlement) {
        if (entitlement.productCode() != null && !entitlement.productCode().isBlank()) {
            return entitlement.productCode();
        }
        return entitlement.featureCode();
    }

    private int value(Integer number) {
        return number == null ? 0 : number;
    }

    private LocalDateTime startOfDay(LocalDate date) {
        return date == null ? null : date.atStartOfDay();
    }

    private LocalDateTime endOfDay(LocalDate date) {
        return date == null ? null : date.atTime(23, 59, 59);
    }

    private LocalDateTime toLocal(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, AppTime.ZONE);
    }

    private <TEnum extends Enum<TEnum>> TEnum enumValue(Class<TEnum> type, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
