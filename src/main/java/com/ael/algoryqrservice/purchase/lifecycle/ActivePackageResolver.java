package com.ael.algoryqrservice.purchase.lifecycle;

import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.service.entitlement.PurchaseSelectionPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ActivePackageResolver {

    private final PurchaseSelectionPolicy purchaseSelectionPolicy;
    private final PurchaseRepository purchaseRepository;

    public Optional<Purchase> currentActive(Long userId) {
        return purchaseSelectionPolicy.highestPriority(
                purchaseSelectionPolicy.usableSubscriptions(userId)
        );
    }

    public Optional<Purchase> latestExpiredSubscriptionLike(Long userId) {
        return purchaseRepository.findByUserIdAndStatus(userId, PurchaseStatus.EXPIRED).stream()
                .filter(purchaseSelectionPolicy::isSubscriptionLike)
                .max(Comparator
                        .comparing(Purchase::getExpiresAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Purchase::getId, Comparator.nullsLast(Comparator.naturalOrder())));
    }
}
