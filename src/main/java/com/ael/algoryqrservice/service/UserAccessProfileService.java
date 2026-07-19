package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.Product;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.UserEntitlement;
import com.ael.algoryqrservice.model.dto.UserAccessProfile;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.repository.PlanPackageRepository;
import com.ael.algoryqrservice.repository.ProductRepository;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.repository.UserEntitlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserAccessProfileService {

    private final PurchaseRepository purchaseRepository;
    private final UserEntitlementRepository entitlementRepository;
    private final PlanPackageRepository planPackageRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public UserAccessProfile resolve(Long userId) {
        List<Purchase> activePurchases = purchaseRepository.findByUserIdAndStatus(userId, PurchaseStatus.ACTIVE);
        Map<Long, Integer> priorityByPackageId = planPackageRepository.findAllById(
                activePurchases.stream().map(Purchase::getPackageId).distinct().toList()
        ).stream().collect(Collectors.toMap(
                planPackage -> planPackage.getId(),
                planPackage -> planPackage.getPriority() == null ? 0 : planPackage.getPriority()
        ));

        Purchase activePurchase = activePurchases.stream()
                .filter(Purchase::isUsable)
                .max(Comparator.comparingInt(purchase -> priorityByPackageId.getOrDefault(purchase.getPackageId(), 0)))
                .orElse(null);

        if (activePurchase == null) {
            return new UserAccessProfile(null, List.of(), List.of());
        }

        Map<Long, Purchase> activeById = activePurchases.stream()
                .filter(Purchase::isUsable)
                .collect(Collectors.toMap(Purchase::getId, Function.identity()));

        List<UserEntitlement> usableEntitlements = entitlementRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(entitlement -> isUsable(entitlement, activeById))
                .toList();

        List<String> products = usableEntitlements.stream()
                .map(UserEntitlement::getProductCode)
                .distinct()
                .sorted()
                .toList();

        Map<String, Product> productsByCode = productRepository.findByCodeIn(products).stream()
                .collect(Collectors.toMap(Product::getCode, Function.identity(), (left, right) -> left));

        List<String> scopes = products.stream()
                .map(productsByCode::get)
                .filter(Objects::nonNull)
                .map(Product::getScopeCode)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();

        return new UserAccessProfile(activePurchase.getPackageCode(), products, scopes);
    }

    private boolean isUsable(UserEntitlement entitlement, Map<Long, Purchase> activeById) {
        Purchase purchase = activeById.get(entitlement.getPurchaseId());
        return purchase != null && entitlement.isUsable(purchase);
    }
}
