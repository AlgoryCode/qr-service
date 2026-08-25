package com.ael.algoryqrservice.service.entitlement;

import com.ael.algoryqrservice.model.FulfillmentDetail;
import com.ael.algoryqrservice.model.FulfillmentUsageLog;
import com.ael.algoryqrservice.model.GrantFulfillment;
import com.ael.algoryqrservice.model.Product;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.UserEntitlement;
import com.ael.algoryqrservice.model.dto.UserEntitlementResponse;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.repository.FulfillmentDetailRepository;
import com.ael.algoryqrservice.repository.FulfillmentUsageLogRepository;
import com.ael.algoryqrservice.repository.GrantFulfillmentRepository;
import com.ael.algoryqrservice.repository.ProductRepository;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.repository.UserEntitlementRepository;
import com.ael.algoryqrservice.util.AppTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Read model for "which rights does this user hold". All lookups are batched, so a user with
 * many entitlements still costs a fixed number of queries.
 */
@Service
@RequiredArgsConstructor
public class UserEntitlementQueryService {

    private static final int RECENT_USAGE_LOG_LIMIT = 200;

    private final FulfillmentDetailRepository fulfillmentDetailRepository;
    private final GrantFulfillmentRepository grantFulfillmentRepository;
    private final FulfillmentUsageLogRepository usageLogRepository;
    private final UserEntitlementRepository entitlementRepository;
    private final PurchaseRepository purchaseRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public List<UserEntitlementResponse> forUser(Long userId) {
        List<FulfillmentDetail> details = fulfillmentDetailRepository.findAllActiveByUserId(userId, AppTime.nowLocal());
        Map<Long, Purchase> purchasesByGrantId = purchasesByGrantId(grantsOf(details));
        return toResponses(details, detail -> purchasesByGrantId.get(detail.getFulfillmentId()), userId);
    }

    @Transactional(readOnly = true)
    public List<UserEntitlementResponse> forPurchase(Purchase purchase) {
        GrantFulfillment grant = grantFulfillmentRepository.findByPurchaseId(purchase.getId()).orElse(null);
        if (grant == null) {
            return legacyEntitlementsOf(purchase);
        }
        List<FulfillmentDetail> details = fulfillmentDetailRepository.findByFulfillmentId(grant.getId());
        return toResponses(details, detail -> purchase, purchase.getUserId());
    }

    private List<UserEntitlementResponse> toResponses(
            List<FulfillmentDetail> details,
            Function<FulfillmentDetail, Purchase> purchaseResolver,
            Long userId
    ) {
        Map<Long, LocalDateTime> lastUsageByDetail = lastUsageByDetail(userId);
        Map<Long, String> productNames = productNames(details.stream()
                .map(FulfillmentDetail::getProductId)
                .filter(Objects::nonNull)
                .distinct()
                .toList());

        return details.stream()
                .map(detail -> toResponse(
                        detail,
                        purchaseResolver.apply(detail),
                        lastUsageByDetail.get(detail.getId()),
                        productNames.getOrDefault(detail.getProductId(), detail.getFeatureCode())
                ))
                .toList();
    }

    private List<UserEntitlementResponse> legacyEntitlementsOf(Purchase purchase) {
        List<UserEntitlement> entitlements =
                entitlementRepository.findByPurchaseIdOrderByProductCodeAsc(purchase.getId());
        Map<Long, String> productNames = productNames(entitlements.stream()
                .map(UserEntitlement::getProductId)
                .filter(Objects::nonNull)
                .distinct()
                .toList());
        return entitlements.stream()
                .map(entitlement -> toResponse(
                        entitlement,
                        purchase,
                        productNames.getOrDefault(entitlement.getProductId(), entitlement.getProductCode())
                ))
                .toList();
    }

    private UserEntitlementResponse toResponse(
            FulfillmentDetail detail,
            Purchase purchase,
            LocalDateTime lastUsage,
            String productName
    ) {
        boolean expired = purchase == null
                || purchase.isEffectivelyExpired()
                || (detail.getExpiresAt() != null && detail.getExpiresAt().isBefore(AppTime.nowLocal()));
        return UserEntitlementResponse.builder()
                .id(detail.getId())
                .productId(detail.getProductId())
                .productCode(detail.getFeatureCode())
                .productName(productName)
                .purchaseId(purchase == null ? null : purchase.getId())
                .totalQuantity(detail.isUnlimited() ? 0 : detail.getQuantity())
                .remainingQuantity(detail.isUnlimited() ? Integer.MAX_VALUE : detail.remainingQuantity())
                .usedQuantity(detail.getUsedQuantity())
                .unlimited(detail.isUnlimited())
                .startsAt(detail.getStartsAt())
                .expiresAt(detail.getExpiresAt())
                .lastUsage(lastUsage)
                .purchaseStatus(purchase == null ? PurchaseStatus.EXPIRED : purchase.getStatus())
                .expired(expired)
                .usable(purchase != null && purchase.isUsable())
                .createdAt(detail.getCreatedAt())
                .build();
    }

    private UserEntitlementResponse toResponse(
            UserEntitlement entitlement,
            Purchase purchase,
            String productName
    ) {
        boolean expired = purchase == null || purchase.isEffectivelyExpired() || entitlement.isExpiredByDate();
        return UserEntitlementResponse.builder()
                .id(entitlement.getId())
                .productId(entitlement.getProductId())
                .productCode(entitlement.getProductCode())
                .productName(productName)
                .purchaseId(entitlement.getPurchaseId())
                .totalQuantity(entitlement.getTotalQuantity())
                .remainingQuantity(entitlement.getRemainingQuantity())
                .usedQuantity(entitlement.getUsedQuantity())
                .unlimited(entitlement.isUnlimited())
                .startsAt(entitlement.getStartsAt())
                .expiresAt(entitlement.getExpiresAt())
                .lastUsage(entitlement.getLastUsage())
                .purchaseStatus(purchase == null ? PurchaseStatus.EXPIRED : purchase.getStatus())
                .expired(expired)
                .usable(purchase != null && entitlement.isUsable(purchase))
                .createdAt(entitlement.getCreatedAt())
                .build();
    }

    private List<GrantFulfillment> grantsOf(List<FulfillmentDetail> details) {
        List<Long> grantIds = details.stream()
                .map(FulfillmentDetail::getFulfillmentId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return grantFulfillmentRepository.findAllById(grantIds);
    }

    private Map<Long, Purchase> purchasesByGrantId(List<GrantFulfillment> grants) {
        Map<Long, Purchase> purchasesById = purchaseRepository.findAllById(grants.stream()
                        .map(GrantFulfillment::getPurchaseId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(Purchase::getId, Function.identity(), (left, right) -> left));

        Map<Long, Purchase> purchasesByGrantId = new HashMap<>();
        for (GrantFulfillment grant : grants) {
            Purchase purchase = purchasesById.get(grant.getPurchaseId());
            if (purchase != null) {
                purchasesByGrantId.put(grant.getId(), purchase);
            }
        }
        return purchasesByGrantId;
    }

    private Map<Long, String> productNames(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return productRepository.findAllById(productIds).stream()
                .filter(product -> product.getName() != null)
                .collect(Collectors.toMap(Product::getId, Product::getName, (left, right) -> left));
    }

    private Map<Long, LocalDateTime> lastUsageByDetail(Long userId) {
        Map<Long, LocalDateTime> lastUsage = new HashMap<>();
        List<FulfillmentUsageLog> logs = usageLogRepository
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, RECENT_USAGE_LOG_LIMIT))
                .getContent();
        for (FulfillmentUsageLog log : logs) {
            lastUsage.putIfAbsent(log.getDetailId(), log.getCreatedAt());
        }
        return lastUsage;
    }
}
