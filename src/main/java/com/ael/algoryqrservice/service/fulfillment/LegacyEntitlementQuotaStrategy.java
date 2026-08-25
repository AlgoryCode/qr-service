package com.ael.algoryqrservice.service.fulfillment;

import com.ael.algoryqrservice.model.Product;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.UserEntitlement;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.repository.ProductRepository;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.repository.UserEntitlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reads quotas from the pre-fulfillment {@code tbl_user_entitlement} rows. Kept for tenants that
 * have not been migrated yet; selected by {@link FulfillmentQuotaStrategyFactory}.
 */
@Component
@RequiredArgsConstructor
public class LegacyEntitlementQuotaStrategy implements FulfillmentQuotaStrategy {

    private final UserEntitlementRepository userEntitlementRepository;
    private final PurchaseRepository purchaseRepository;
    private final ProductRepository productRepository;

    @Override
    public boolean hasScope(Long userId, String scopeCode) {
        List<UserEntitlement> entitlements = userEntitlementRepository.findByUserIdOrderByCreatedAtDesc(userId);
        Map<Long, Purchase> purchasesById = purchasesOf(entitlements);
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

    @Override
    public int sumAddonQuantity(Long userId, String featureCode) {
        List<UserEntitlement> entitlements = userEntitlementRepository.findByUserIdOrderByCreatedAtDesc(userId);
        Map<Long, Purchase> purchasesById = purchasesOf(entitlements);

        int total = 0;
        for (UserEntitlement entitlement : entitlements) {
            if (!isActiveAddon(entitlement, featureCode, purchasesById)) {
                continue;
            }
            if (entitlement.isUnlimited()) {
                return Integer.MAX_VALUE;
            }
            total += quantityOf(entitlement);
        }
        return total;
    }

    @Override
    public boolean supportsLedger() {
        return false;
    }

    private int quantityOf(UserEntitlement entitlement) {
        Integer total = entitlement.getTotalQuantity();
        return total == null ? 0 : total;
    }

    private boolean isActiveAddon(
            UserEntitlement entitlement,
            String featureCode,
            Map<Long, Purchase> purchasesById
    ) {
        if (!Objects.equals(entitlement.getProductCode(), featureCode)) {
            return false;
        }
        Purchase purchase = purchasesById.get(entitlement.getPurchaseId());
        if (purchase == null || !purchase.isUsable() || purchase.getPurchaseType() != PurchaseType.ADD_ON) {
            return false;
        }
        return entitlement.isStartedByDate() && !entitlement.isExpiredByDate();
    }

    private Map<Long, Purchase> purchasesOf(List<UserEntitlement> entitlements) {
        List<Long> purchaseIds = entitlements.stream()
                .map(UserEntitlement::getPurchaseId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return purchaseRepository.findAllById(purchaseIds).stream()
                .collect(Collectors.toMap(Purchase::getId, Function.identity(), (left, right) -> left));
    }
}
