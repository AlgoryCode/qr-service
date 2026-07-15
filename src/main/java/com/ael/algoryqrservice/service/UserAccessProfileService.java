package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.UserEntitlement;
import com.ael.algoryqrservice.model.dto.UserAccessProfile;
import com.ael.algoryqrservice.model.enums.PackageCode;
import com.ael.algoryqrservice.model.enums.ProductCode;
import com.ael.algoryqrservice.model.enums.ProductScope;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.repository.UserEntitlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserAccessProfileService {

    private final PurchaseRepository purchaseRepository;
    private final UserEntitlementRepository entitlementRepository;

    @Transactional(readOnly = true)
    public UserAccessProfile resolve(Long userId) {
        List<Purchase> activePurchases = purchaseRepository.findByUserIdAndStatus(userId, PurchaseStatus.ACTIVE);
        Purchase activePurchase = activePurchases.stream()
                .filter(Purchase::isUsable)
                .max(Comparator.comparingInt(this::packagePriority))
                .orElse(null);

        if (activePurchase == null) {
            return new UserAccessProfile(null, List.of(), List.of());
        }

        Map<Long, Purchase> activeById = activePurchases.stream()
                .filter(Purchase::isUsable)
                .collect(Collectors.toMap(Purchase::getId, Function.identity()));

        List<ProductCode> products = entitlementRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(entitlement -> isUsable(entitlement, activeById))
                .map(UserEntitlement::getProductCode)
                .distinct()
                .sorted()
                .toList();

        List<ProductScope> scopes = products.stream()
                .map(this::toScope)
                .filter(java.util.Objects::nonNull)
                .toList();

        return new UserAccessProfile(activePurchase.getPackageCode(), products, scopes);
    }

    private boolean isUsable(UserEntitlement entitlement, Map<Long, Purchase> activeById) {
        Purchase purchase = activeById.get(entitlement.getPurchaseId());
        return purchase != null && entitlement.isUsable(purchase.getStatus());
    }

    private int packagePriority(Purchase purchase) {
        return purchase.getPackageCode() == PackageCode.PRO_PACKAGE ? 2 : 1;
    }

    private ProductScope toScope(ProductCode productCode) {
        return switch (productCode) {
            case QR_CREATE -> ProductScope.QR_CREATE_OWNER;
            case QR_MENU -> ProductScope.QR_MENU_OWNER;
            case QR_AGENT -> null;
        };
    }
}
