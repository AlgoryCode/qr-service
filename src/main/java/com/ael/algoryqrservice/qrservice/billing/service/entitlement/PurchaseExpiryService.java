package com.ael.algoryqrservice.qrservice.billing.service.entitlement;

import com.ael.algoryqrservice.qrservice.event.PurchasesExpiredEvent;
import com.ael.algoryqrservice.qrservice.model.Purchase;
import com.ael.algoryqrservice.qrservice.model.enums.PurchaseLogAction;
import com.ael.algoryqrservice.qrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.qrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.qrservice.billing.service.PurchaseLogService;
import com.ael.algoryqrservice.qrservice.util.WritableTransactionGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Moves due purchases to EXPIRED. Downstream state is refreshed by listeners of
 * {@link PurchasesExpiredEvent} rather than by direct calls, which keeps this service
 * free of the subscription/menu dependency cycle.
 */
@Service
@RequiredArgsConstructor
public class PurchaseExpiryService {

    private final PurchaseRepository purchaseRepository;
    private final PurchaseLogService purchaseLogService;
    private final ApplicationEventPublisher eventPublisher;
    private final WritableTransactionGuard writableTransactionGuard;

    @Transactional
    public void expireAllDue() {
        List<Purchase> duePurchases = purchaseRepository.findByStatusAndExpiresAtBefore(
                PurchaseStatus.ACTIVE, LocalDateTime.now()
        );
        expireAndAnnounce(duePurchases);
    }

    @Transactional
    public void expireDueForUser(Long merchantId) {
        if (merchantId == null || !writableTransactionGuard.allowsWrites("purchase expiry", merchantId)) {
            return;
        }
        List<Purchase> duePurchases = purchaseRepository.findByMerchantIdAndStatusAndExpiresAtBefore(
                merchantId, PurchaseStatus.ACTIVE, LocalDateTime.now()
        );
        expireAndAnnounce(duePurchases);
    }

    @Transactional
    public void expire(Purchase purchase) {
        expireAndAnnounce(List.of(purchase));
    }

    private void expireAndAnnounce(List<Purchase> purchases) {
        Set<Long> affectedUserIds = new LinkedHashSet<>();
        for (Purchase purchase : purchases) {
            if (expireOne(purchase)) {
                affectedUserIds.add(purchase.getMerchantId());
            }
        }
        affectedUserIds.remove(null);
        if (!affectedUserIds.isEmpty()) {
            eventPublisher.publishEvent(new PurchasesExpiredEvent(new HashSet<>(affectedUserIds)));
        }
    }

    private boolean expireOne(Purchase purchase) {
        if (purchase.getStatus() != PurchaseStatus.ACTIVE) {
            return false;
        }
        purchase.setStatus(PurchaseStatus.EXPIRED);
        purchaseRepository.save(purchase);

        purchaseLogService.log(
                purchase.getId(),
                purchase.getMerchantId(),
                PurchaseLogAction.PURCHASE_EXPIRED,
                purchase.getPackageName() + " paketi süresi doldu (" + purchase.getExpiresAt() + ")"
        );
        return true;
    }
}
