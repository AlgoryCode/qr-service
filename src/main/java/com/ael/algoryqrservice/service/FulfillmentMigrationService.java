package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.FulfillmentDetail;
import com.ael.algoryqrservice.model.GrantFulfillment;
import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.PlanPackageItem;
import com.ael.algoryqrservice.model.Product;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.UserEntitlement;
import com.ael.algoryqrservice.model.enums.FulfillmentDetailSource;
import com.ael.algoryqrservice.model.enums.GrantFulfillmentStatus;
import com.ael.algoryqrservice.model.enums.ProductType;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.repository.FulfillmentDetailRepository;
import com.ael.algoryqrservice.repository.GrantFulfillmentRepository;
import com.ael.algoryqrservice.repository.PlanPackageRepository;
import com.ael.algoryqrservice.repository.ProductRepository;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.repository.UserEntitlementRepository;
import com.ael.algoryqrservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FulfillmentMigrationService {

    private final PurchaseRepository purchaseRepository;
    private final UserEntitlementRepository userEntitlementRepository;
    private final GrantFulfillmentRepository grantFulfillmentRepository;
    private final FulfillmentDetailRepository fulfillmentDetailRepository;
    private final PlanPackageRepository planPackageRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @Transactional
    public MigrationResult backfillUser(Long userId) {
        List<Purchase> activePurchases = purchaseRepository.findByUserIdAndStatus(userId, PurchaseStatus.ACTIVE).stream()
                .filter(Purchase::isUsable)
                .toList();

        int fulfillmentCount = 0;
        int detailCount = 0;

        for (Purchase purchase : activePurchases) {
            GrantFulfillment existing = grantFulfillmentRepository.findByPurchaseId(purchase.getId()).orElse(null);
            if (existing != null) {
                if (fulfillmentDetailRepository.findByFulfillmentId(existing.getId()).isEmpty()) {
                    if (purchase.getPurchaseType() == PurchaseType.ADD_ON) {
                        detailCount += fillAddonDetails(purchase, existing);
                    } else if (purchase.getPackageId() != null) {
                        detailCount += fillPackageDetails(purchase, existing);
                    }
                }
                continue;
            }
            if (purchase.getPurchaseType() == PurchaseType.ADD_ON) {
                int details = backfillAddonPurchase(purchase);
                detailCount += details;
                if (details > 0) fulfillmentCount++;
            } else if (purchase.getPackageId() != null) {
                int details = backfillPackagePurchase(purchase);
                detailCount += details;
                if (details > 0) fulfillmentCount++;
            }
        }
        log.info("Backfill complete for userId={}: {} fulfillments, {} details", userId, fulfillmentCount, detailCount);
        return new MigrationResult(userId, fulfillmentCount, detailCount);
    }

    @Transactional
    public int backfillAllActiveUsers() {
        List<Long> userIds = purchaseRepository.findDistinctUserIdsByActiveStatus();
        int migrated = 0;
        for (Long userId : userIds) {
            try {
                MigrationResult result = backfillUser(userId);
                if (result.fulfillmentCount() > 0 || result.detailCount() > 0) {
                    migrated++;
                }
            } catch (Exception e) {
                log.error("Backfill failed for userId={}: {}", userId, e.getMessage(), e);
            }
        }
        return migrated;
    }

    public List<MigrationResult> backfillBatch(int offset, int batchSize) {
        List<Long> userIds = userRepository.findAll(PageRequest.of(offset / batchSize, batchSize))
                .stream()
                .map(com.ael.algoryqrservice.model.User::getId)
                .toList();
        List<MigrationResult> results = new ArrayList<>();
        for (Long userId : userIds) {
            try {
                results.add(backfillUser(userId));
            } catch (Exception e) {
                log.error("Backfill failed for userId={}: {}", userId, e.getMessage(), e);
            }
        }
        return results;
    }

    @Transactional(readOnly = true)
    public ParityReport parityReport(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        List<UserEntitlement> entitlements = userEntitlementRepository.findByUserIdOrderByCreatedAtDesc(userId);
        Map<Long, Purchase> purchasesById = purchaseRepository.findAllById(
                entitlements.stream().map(UserEntitlement::getPurchaseId).filter(Objects::nonNull).distinct().toList()
        ).stream().collect(Collectors.toMap(Purchase::getId, Function.identity(), (a, b) -> a));

        List<GrantFulfillment> fulfillments = grantFulfillmentRepository.findByUserIdAndStatus(userId, GrantFulfillmentStatus.ACTIVE);
        List<FulfillmentDetail> details = fulfillmentDetailRepository.findAllActiveByUserId(userId, now);

        List<String> mismatches = new ArrayList<>();

        Map<String, Integer> entitlementTotals = entitlements.stream()
                .filter(e -> {
                    Purchase p = purchasesById.get(e.getPurchaseId());
                    return p != null && p.isUsable() && e.isStartedByDate() && !e.isExpiredByDate();
                })
                .collect(Collectors.groupingBy(
                        UserEntitlement::getProductCode,
                        Collectors.summingInt(e -> e.getTotalQuantity() == null ? 0 : e.getTotalQuantity())
                ));

        Map<String, Integer> detailTotals = details.stream()
                .collect(Collectors.groupingBy(
                        FulfillmentDetail::getFeatureCode,
                        Collectors.summingInt(d -> d.isUnlimited() ? Integer.MAX_VALUE / 2 : d.getQuantity())
                ));

        for (Map.Entry<String, Integer> entry : entitlementTotals.entrySet()) {
            String code = entry.getKey();
            int entitlementQty = entry.getValue();
            int detailQty = detailTotals.getOrDefault(code, 0);
            if (Math.abs(entitlementQty - detailQty) > 0) {
                mismatches.add("QUOTA_MISMATCH:" + code + " entitlement=" + entitlementQty + " detail=" + detailQty);
            }
        }

        return new ParityReport(userId, mismatches.isEmpty(), mismatches, fulfillments.size(), details.size());
    }

    private int backfillPackagePurchase(Purchase purchase) {
        PlanPackage planPackage = planPackageRepository.findByIdWithItems(purchase.getPackageId()).orElse(null);
        if (planPackage == null) {
            return 0;
        }
        GrantFulfillment fulfillment = grantFulfillmentRepository.save(GrantFulfillment.builder()
                .userId(purchase.getUserId())
                .purchaseId(purchase.getId())
                .paymentId(purchase.getPaymentId())
                .packageId(purchase.getPackageId())
                .status(GrantFulfillmentStatus.ACTIVE)
                .startsAt(purchase.getStartsAt())
                .expiresAt(purchase.getExpiresAt())
                .migrationKey("backfill-v1:" + purchase.getId())
                .build());

        return fillPackageDetails(purchase, fulfillment);
    }

    private int fillPackageDetails(Purchase purchase, GrantFulfillment fulfillment) {
        PlanPackage planPackage = planPackageRepository.findByIdWithItems(purchase.getPackageId()).orElse(null);
        if (planPackage == null) {
            return 0;
        }
        int detailCount = 0;
        List<UserEntitlement> purchaseEntitlements = userEntitlementRepository.findByPurchaseIdOrderByProductCodeAsc(purchase.getId());
        Map<Long, UserEntitlement> byProductId = purchaseEntitlements.stream()
                .collect(Collectors.toMap(UserEntitlement::getProductId, Function.identity(), (a, b) -> a));

        for (PlanPackageItem item : planPackage.getItems()) {
            Product product = item.getProduct();
            UserEntitlement ent = byProductId.get(product.getId());
            int quantity = ent != null && ent.getTotalQuantity() != null ? ent.getTotalQuantity() : item.getQuantity();
            int usedQty = ent != null && ent.getUsedQuantity() != null ? ent.getUsedQuantity() : 0;
            boolean unlimited = ent != null ? ent.isUnlimited() : item.isUnlimited();
            String featureCode = resolveFeatureCode(product);
            if (!unlimited && quantity > 0) {
                usedQty = Math.min(usedQty, quantity);
            }

            fulfillmentDetailRepository.save(FulfillmentDetail.builder()
                    .fulfillmentId(fulfillment.getId())
                    .userId(purchase.getUserId())
                    .productId(product.getId())
                    .productTypeId(ProductType.PACKAGE_PRODUCT)
                    .featureCode(featureCode)
                    .scopeCode(product.getScopeCode())
                    .quantity(unlimited ? 0 : quantity)
                    .unlimited(unlimited)
                    .usedQuantity(usedQty)
                    .source(FulfillmentDetailSource.PACKAGE_INCLUDE)
                    .startsAt(purchase.getStartsAt())
                    .expiresAt(purchase.getExpiresAt())
                    .build());
            detailCount++;
        }
        return detailCount;
    }

    private int backfillAddonPurchase(Purchase purchase) {
        if (purchase.getPackageCode() == null || purchase.getPackageId() == null) {
            return 0;
        }
        GrantFulfillment fulfillment = grantFulfillmentRepository.save(GrantFulfillment.builder()
                .userId(purchase.getUserId())
                .purchaseId(purchase.getId())
                .paymentId(purchase.getPaymentId())
                .packageId(purchase.getPackageId())
                .status(GrantFulfillmentStatus.ACTIVE)
                .startsAt(purchase.getStartsAt())
                .expiresAt(purchase.getExpiresAt())
                .migrationKey("backfill-v1:" + purchase.getId())
                .build());

        return fillAddonDetails(purchase, fulfillment);
    }

    private int fillAddonDetails(Purchase purchase, GrantFulfillment fulfillment) {
        String productCode = purchase.getPackageCode();
        if (productCode == null) {
            return 0;
        }
        Product product = productRepository.findByCode(productCode).orElse(null);
        List<UserEntitlement> purchaseEntitlements = userEntitlementRepository.findByPurchaseIdOrderByProductCodeAsc(purchase.getId());
        UserEntitlement ent = purchaseEntitlements.stream()
                .filter(e -> Objects.equals(e.getProductCode(), productCode))
                .findFirst().orElse(null);

        int quantity = ent != null && ent.getTotalQuantity() != null ? ent.getTotalQuantity()
                : (purchase.getInstallmentCount() != null ? purchase.getInstallmentCount() : 1);
        int usedQty = ent != null && ent.getUsedQuantity() != null ? ent.getUsedQuantity() : 0;
        if (quantity > 0) {
            usedQty = Math.min(usedQty, quantity);
        }
        String featureCode = product != null ? resolveFeatureCode(product) : productCode;
        String scopeCode = product != null ? product.getScopeCode() : null;

        fulfillmentDetailRepository.save(FulfillmentDetail.builder()
                .fulfillmentId(fulfillment.getId())
                .userId(purchase.getUserId())
                .productId(product != null ? product.getId() : null)
                .productTypeId(ProductType.ADDON_PRODUCT)
                .featureCode(featureCode)
                .scopeCode(scopeCode)
                .quantity(quantity)
                .unlimited(false)
                .usedQuantity(usedQty)
                .source(FulfillmentDetailSource.ADDON_PURCHASE)
                .startsAt(purchase.getStartsAt())
                .expiresAt(purchase.getExpiresAt())
                .build());
        return 1;
    }

    private String resolveFeatureCode(Product product) {
        if (product.getFeatureCode() != null && !product.getFeatureCode().isBlank()) {
            return product.getFeatureCode();
        }
        return product.getCode();
    }

    public record MigrationResult(Long userId, int fulfillmentCount, int detailCount) {}
    public record ParityReport(Long userId, boolean ok, List<String> mismatches, int fulfillmentCount, int detailCount) {}
}
