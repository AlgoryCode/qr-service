package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.ForbiddenException;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.UserEntitlement;
import com.ael.algoryqrservice.model.dto.UserEntitlementResponse;
import com.ael.algoryqrservice.model.enums.ProductCode;
import com.ael.algoryqrservice.model.enums.PurchaseLogAction;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.repository.ProductRepository;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.repository.UserEntitlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EntitlementService {

    private final UserEntitlementRepository entitlementRepository;
    private final PurchaseRepository purchaseRepository;
    private final ProductRepository productRepository;
    private final PurchaseLogService purchaseLogService;

    @Transactional
    public void grant(Purchase purchase, Long productId, ProductCode productCode, int quantity) {
        UserEntitlement entitlement = UserEntitlement.builder()
                .userId(purchase.getUserId())
                .productId(productId)
                .productCode(productCode)
                .purchaseId(purchase.getId())
                .totalQuantity(quantity)
                .remainingQuantity(quantity)
                .usedQuantity(0)
                .startsAt(purchase.getStartsAt())
                .expiresAt(purchase.getExpiresAt())
                .build();

        entitlementRepository.save(entitlement);
        purchaseLogService.log(
                purchase.getId(),
                purchase.getUserId(),
                PurchaseLogAction.ENTITLEMENT_GRANTED,
                quantity + " adet " + productCode + " hakkı tanımlandı ("
                        + purchase.getStartsAt() + " - " + purchase.getExpiresAt() + ")"
        );
    }

    @Transactional
    public void consume(Long userId, ProductCode productCode, int amount) {
        expireDuePurchases();

        List<UserEntitlement> entitlements = entitlementRepository
                .findByUserIdAndRemainingQuantityGreaterThanOrderByCreatedAtAsc(userId, 0);

        Map<Long, Purchase> purchasesById = loadPurchases(entitlements);

        int remainingToConsume = amount;
        Long purchaseIdForLog = null;

        for (UserEntitlement entitlement : entitlements) {
            if (!entitlement.getProductCode().equals(productCode)) {
                continue;
            }

            Purchase purchase = purchasesById.get(entitlement.getPurchaseId());
            if (purchase == null || !entitlement.isUsable(purchase.getStatus())) {
                continue;
            }

            if (remainingToConsume <= 0) {
                break;
            }

            int consumed = Math.min(entitlement.getRemainingQuantity(), remainingToConsume);
            entitlement.setRemainingQuantity(entitlement.getRemainingQuantity() - consumed);
            entitlement.setUsedQuantity(entitlement.getUsedQuantity() + consumed);
            entitlementRepository.save(entitlement);

            remainingToConsume -= consumed;
            purchaseIdForLog = entitlement.getPurchaseId();
        }

        if (remainingToConsume > 0) {
            throw new ForbiddenException("Yetersiz veya süresi dolmuş " + productCode + " hakkı. Lütfen paket satın alın.");
        }

        if (purchaseIdForLog != null) {
            purchaseLogService.log(
                    purchaseIdForLog,
                    userId,
                    PurchaseLogAction.ENTITLEMENT_CONSUMED,
                    amount + " adet " + productCode + " hakkı kullanıldı"
            );
        }
    }

    @Transactional(readOnly = true)
    public List<UserEntitlementResponse> getUserEntitlements(Long userId) {
        Map<Long, Purchase> purchasesById = loadPurchases(
                entitlementRepository.findByUserIdOrderByCreatedAtDesc(userId)
        );

        return entitlementRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(entitlement -> toResponse(entitlement, purchasesById.get(entitlement.getPurchaseId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UserEntitlementResponse> getPurchaseEntitlements(Purchase purchase) {
        return entitlementRepository.findByPurchaseIdOrderByProductCodeAsc(purchase.getId()).stream()
                .map(entitlement -> toResponse(entitlement, purchase))
                .toList();
    }

    @Transactional
    public void expireDuePurchases() {
        List<Purchase> duePurchases = purchaseRepository.findByStatusAndExpiresAtBefore(
                PurchaseStatus.ACTIVE,
                LocalDateTime.now()
        );

        duePurchases.forEach(this::expirePurchaseInternal);
    }

    @Transactional
    public void expirePurchase(Purchase purchase) {
        expirePurchaseInternal(purchase);
    }

    private void expirePurchaseInternal(Purchase purchase) {
        if (purchase.getStatus() != PurchaseStatus.ACTIVE) {
            return;
        }

        purchase.setStatus(PurchaseStatus.EXPIRED);
        purchaseRepository.save(purchase);

        purchaseLogService.log(
                purchase.getId(),
                purchase.getUserId(),
                PurchaseLogAction.PURCHASE_EXPIRED,
                purchase.getPackageName() + " paketi süresi doldu (" + purchase.getExpiresAt() + ")"
        );
    }

    private Map<Long, Purchase> loadPurchases(List<UserEntitlement> entitlements) {
        List<Long> purchaseIds = entitlements.stream()
                .map(UserEntitlement::getPurchaseId)
                .distinct()
                .toList();

        return purchaseRepository.findAllById(purchaseIds).stream()
                .collect(Collectors.toMap(Purchase::getId, Function.identity()));
    }

    UserEntitlementResponse toResponse(UserEntitlement entitlement, Purchase purchase) {
        String productName = productRepository.findById(entitlement.getProductId())
                .map(product -> product.getName())
                .orElse(entitlement.getProductCode().name());

        PurchaseStatus purchaseStatus = purchase != null ? purchase.getStatus() : PurchaseStatus.EXPIRED;
        boolean expired = purchase == null
                || purchase.getStatus() == PurchaseStatus.EXPIRED
                || purchase.getStatus() == PurchaseStatus.CANCELLED
                || entitlement.isExpiredByDate()
                || purchase.isExpiredByDate();
        boolean usable = purchase != null && entitlement.isUsable(purchase.getStatus());

        return UserEntitlementResponse.builder()
                .id(entitlement.getId())
                .productId(entitlement.getProductId())
                .productCode(entitlement.getProductCode())
                .productName(productName)
                .purchaseId(entitlement.getPurchaseId())
                .totalQuantity(entitlement.getTotalQuantity())
                .remainingQuantity(entitlement.getRemainingQuantity())
                .usedQuantity(entitlement.getUsedQuantity())
                .startsAt(entitlement.getStartsAt())
                .expiresAt(entitlement.getExpiresAt())
                .purchaseStatus(purchaseStatus)
                .expired(expired)
                .usable(usable)
                .createdAt(entitlement.getCreatedAt())
                .build();
    }
}
