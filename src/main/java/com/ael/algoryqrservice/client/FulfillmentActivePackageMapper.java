package com.ael.algoryqrservice.client;

import com.ael.algoryqrservice.client.dto.ExternalActivePackageResponse;
import com.ael.algoryqrservice.model.dto.PurchaseSummaryResponse;
import com.ael.algoryqrservice.model.dto.UserEntitlementResponse;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class FulfillmentActivePackageMapper {

    public PurchaseSummaryResponse toSummary(ExternalActivePackageResponse source) {
        LocalDateTime startsAt = source.periodStart() == null ? null : source.periodStart().atStartOfDay();
        LocalDateTime expiresAt = source.periodEnd() == null ? null : source.periodEnd().atTime(23, 59, 59);
        LocalDateTime purchasedAt = source.activatedAt() == null
                ? null
                : LocalDateTime.ofInstant(source.activatedAt(), ZoneOffset.UTC);
        boolean expired = expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
        boolean active = "ACTIVE".equals(source.status()) && !expired;
        Integer daysUntilExpiry = source.periodEnd() == null
                ? null
                : (int) ChronoUnit.DAYS.between(LocalDate.now(), source.periodEnd());
        return PurchaseSummaryResponse.builder()
                .purchaseId(source.purchaseId())
                .userId(source.userId())
                .packageId(source.packageId())
                .packageCode(source.packageCode())
                .packageName(source.packageName())
                .status(active ? PurchaseStatus.ACTIVE : PurchaseStatus.EXPIRED)
                .purchaseType(PurchaseType.PAID)
                .startsAt(startsAt)
                .expiresAt(expiresAt)
                .purchasedAt(purchasedAt)
                .daysUntilExpiry(daysUntilExpiry)
                .expired(expired)
                .usable(active)
                .products(products(source))
                .build();
    }

    private List<UserEntitlementResponse> products(ExternalActivePackageResponse source) {
        if (source.products() == null) {
            return List.of();
        }
        return source.products().stream()
                .map(code -> UserEntitlementResponse.builder()
                        .productCode(code)
                        .productName(code)
                        .purchaseId(source.purchaseId())
                        .unlimited(true)
                        .usable(true)
                        .expired(false)
                        .purchaseStatus(PurchaseStatus.ACTIVE)
                        .build())
                .toList();
    }
}
