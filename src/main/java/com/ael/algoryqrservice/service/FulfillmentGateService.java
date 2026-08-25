package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.config.AppProperties;
import com.ael.algoryqrservice.model.FulfillmentDetail;
import com.ael.algoryqrservice.model.FulfillmentUsageLog;
import com.ael.algoryqrservice.model.Product;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.UserEntitlement;
import com.ael.algoryqrservice.model.enums.FulfillmentDetailSource;
import com.ael.algoryqrservice.model.enums.FulfillmentGateMode;
import com.ael.algoryqrservice.model.enums.FulfillmentReferenceType;
import com.ael.algoryqrservice.model.enums.FulfillmentUsageAction;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.repository.FulfillmentDetailRepository;
import com.ael.algoryqrservice.repository.FulfillmentUsageLogRepository;
import com.ael.algoryqrservice.repository.GrantFulfillmentRepository;
import com.ael.algoryqrservice.repository.ProductRepository;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.repository.UserEntitlementRepository;
import com.ael.algoryqrservice.util.AppTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FulfillmentGateService {

    private final GrantFulfillmentRepository grantFulfillmentRepository;
    private final FulfillmentDetailRepository fulfillmentDetailRepository;
    private final FulfillmentUsageLogRepository usageLogRepository;
    private final UserEntitlementRepository userEntitlementRepository;
    private final PurchaseRepository purchaseRepository;
    private final ProductRepository productRepository;
    private final AppProperties appProperties;

    @Transactional(readOnly = true)
    public boolean hasScope(Long userId, String scopeCode) {
        FulfillmentGateMode mode = appProperties.getFulfillment().getGateMode();
        if (mode == FulfillmentGateMode.ENTITLEMENT_ONLY) {
            return hasScopeLegacy(userId, scopeCode);
        }
        if (mode == FulfillmentGateMode.FULFILLMENT_ONLY) {
            return hasScopeFromDetail(userId, scopeCode);
        }
        if (grantFulfillmentRepository.existsByUserId(userId)) {
            return hasScopeFromDetail(userId, scopeCode);
        }
        return hasScopeLegacy(userId, scopeCode);
    }

    @Transactional(readOnly = true)
    public int sumAddonQuantity(Long userId, String featureCode) {
        FulfillmentGateMode mode = appProperties.getFulfillment().getGateMode();
        if (mode == FulfillmentGateMode.ENTITLEMENT_ONLY) {
            return sumAddonQuantityLegacy(userId, featureCode);
        }
        if (mode == FulfillmentGateMode.FULFILLMENT_ONLY) {
            return sumAddonQuantityFromDetail(userId, featureCode);
        }
        if (grantFulfillmentRepository.existsByUserId(userId)) {
            return sumAddonQuantityFromDetail(userId, featureCode);
        }
        return sumAddonQuantityLegacy(userId, featureCode);
    }

    @Transactional(readOnly = true)
    public int sumAddonUsedQuantity(Long userId, String featureCode) {
        FulfillmentGateMode mode = appProperties.getFulfillment().getGateMode();
        if (mode == FulfillmentGateMode.ENTITLEMENT_ONLY) {
            return sumAddonUsedQuantityLegacy(userId, featureCode);
        }
        if (mode == FulfillmentGateMode.FULFILLMENT_ONLY || grantFulfillmentRepository.existsByUserId(userId)) {
            return sumAddonUsedQuantityFromDetail(userId, featureCode);
        }
        return sumAddonUsedQuantityLegacy(userId, featureCode);
    }

    @Transactional
    public void consumeAddon(Long userId, String featureCode, int amount, FulfillmentReferenceType referenceType, Long referenceId) {
        if (!shouldUseDetail(userId)) {
            return;
        }
        LocalDateTime now = AppTime.nowLocal();
        List<FulfillmentDetail> details = fulfillmentDetailRepository.findAndLockActiveByFeatureCodeAndSource(
                userId, featureCode, FulfillmentDetailSource.ADDON_PURCHASE, now
        );
        int remaining = amount;
        for (FulfillmentDetail detail : details) {
            if (remaining <= 0) {
                break;
            }
            if (detail.isUnlimited()) {
                writeUsageLog(detail, userId, FulfillmentUsageAction.CONSUME, amount, referenceType, referenceId);
                return;
            }
            int available = detail.remainingQuantity();
            if (available <= 0) {
                continue;
            }
            int consumed = Math.min(available, remaining);
            detail.setUsedQuantity(detail.getUsedQuantity() + consumed);
            fulfillmentDetailRepository.save(detail);
            writeUsageLog(detail, userId, FulfillmentUsageAction.CONSUME, consumed, referenceType, referenceId);
            remaining -= consumed;
        }
    }

    @Transactional
    public void releaseAddon(Long userId, String featureCode, int amount, FulfillmentReferenceType referenceType, Long referenceId) {
        if (!shouldUseDetail(userId)) {
            return;
        }
        LocalDateTime now = AppTime.nowLocal();
        List<FulfillmentDetail> details = fulfillmentDetailRepository.findAndLockActiveByFeatureCodeAndSource(
                userId, featureCode, FulfillmentDetailSource.ADDON_PURCHASE, now
        );
        int remaining = amount;
        for (FulfillmentDetail detail : details) {
            if (remaining <= 0) {
                break;
            }
            if (detail.isUnlimited()) {
                writeUsageLog(detail, userId, FulfillmentUsageAction.RELEASE, amount, referenceType, referenceId);
                return;
            }
            int used = detail.getUsedQuantity();
            if (used <= 0) {
                continue;
            }
            int released = Math.min(used, remaining);
            detail.setUsedQuantity(used - released);
            fulfillmentDetailRepository.save(detail);
            writeUsageLog(detail, userId, FulfillmentUsageAction.RELEASE, released, referenceType, referenceId);
            remaining -= released;
        }
    }

    private boolean hasScopeFromDetail(Long userId, String scopeCode) {
        LocalDateTime now = AppTime.nowLocal();
        return fulfillmentDetailRepository.existsActiveByScopeCode(userId, scopeCode, now);
    }

    private boolean hasScopeLegacy(Long userId, String scopeCode) {
        List<UserEntitlement> entitlements = userEntitlementRepository.findByUserIdOrderByCreatedAtDesc(userId);
        Map<Long, Purchase> purchasesById = loadPurchases(entitlements);
        Map<String, Product> productsByCode = productRepository.findByCodeIn(
                entitlements.stream().map(UserEntitlement::getProductCode).distinct().toList()
        ).stream().collect(Collectors.toMap(Product::getCode, Function.identity(), (left, right) -> left));

        return entitlements.stream().anyMatch(entitlement -> {
            Product product = productsByCode.get(entitlement.getProductCode());
            if (product == null || !Objects.equals(product.getScopeCode(), scopeCode)) {
                return false;
            }
            Purchase purchase = purchasesById.get(entitlement.getPurchaseId());
            return purchase != null && entitlement.grantsScope(purchase);
        });
    }

    private int sumAddonQuantityFromDetail(Long userId, String featureCode) {
        LocalDateTime now = AppTime.nowLocal();
        return fulfillmentDetailRepository.sumActiveAddonQuantityByFeatureCode(userId, featureCode, now);
    }

    private int sumAddonUsedQuantityFromDetail(Long userId, String featureCode) {
        LocalDateTime now = AppTime.nowLocal();
        return fulfillmentDetailRepository.sumActiveAddonUsedQuantityByFeatureCode(userId, featureCode, now);
    }

    private int sumAddonQuantityLegacy(Long userId, String featureCode) {
        List<UserEntitlement> entitlements = userEntitlementRepository.findByUserIdOrderByCreatedAtDesc(userId);
        Map<Long, Purchase> purchases = loadPurchases(entitlements);
        int total = 0;
        for (UserEntitlement entitlement : entitlements) {
            if (!Objects.equals(entitlement.getProductCode(), featureCode)) {
                continue;
            }
            Purchase purchase = purchases.get(entitlement.getPurchaseId());
            if (purchase == null || !purchase.isUsable() || purchase.getPurchaseType() != PurchaseType.ADD_ON) {
                continue;
            }
            if (!entitlement.isStartedByDate() || entitlement.isExpiredByDate()) {
                continue;
            }
            if (entitlement.isUnlimited()) {
                return Integer.MAX_VALUE;
            }
            total += entitlement.getTotalQuantity() == null ? 0 : entitlement.getTotalQuantity();
        }
        return total;
    }

    private int sumAddonUsedQuantityLegacy(Long userId, String featureCode) {
        List<UserEntitlement> entitlements = userEntitlementRepository.findByUserIdOrderByCreatedAtDesc(userId);
        Map<Long, Purchase> purchases = loadPurchases(entitlements);
        int total = 0;
        for (UserEntitlement entitlement : entitlements) {
            if (!Objects.equals(entitlement.getProductCode(), featureCode)) {
                continue;
            }
            Purchase purchase = purchases.get(entitlement.getPurchaseId());
            if (purchase == null || !purchase.isUsable() || purchase.getPurchaseType() != PurchaseType.ADD_ON) {
                continue;
            }
            if (!entitlement.isStartedByDate() || entitlement.isExpiredByDate()) {
                continue;
            }
            total += entitlement.getUsedQuantity() == null ? 0 : entitlement.getUsedQuantity();
        }
        return total;
    }

    private boolean shouldUseDetail(Long userId) {
        FulfillmentGateMode mode = appProperties.getFulfillment().getGateMode();
        if (mode == FulfillmentGateMode.ENTITLEMENT_ONLY) {
            return false;
        }
        if (mode == FulfillmentGateMode.FULFILLMENT_ONLY) {
            return true;
        }
        return grantFulfillmentRepository.existsByUserId(userId);
    }

    private Map<Long, Purchase> loadPurchases(List<UserEntitlement> entitlements) {
        List<Long> ids = entitlements.stream()
                .map(UserEntitlement::getPurchaseId)
                .distinct()
                .toList();
        return purchaseRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Purchase::getId, Function.identity()));
    }

    private void writeUsageLog(FulfillmentDetail detail, Long userId, FulfillmentUsageAction action,
                               int amount, FulfillmentReferenceType referenceType, Long referenceId) {
        usageLogRepository.save(FulfillmentUsageLog.builder()
                .detailId(detail.getId())
                .userId(userId)
                .action(action)
                .amount(amount)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .build());
    }
}
