package com.ael.algoryqrservice.config;

import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.service.PurchaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class PendingPurchaseScheduler {

    private final PurchaseService purchaseService;
    private final PurchaseRepository purchaseRepository;
    private final PaymentClientProperties paymentClientProperties;

    @Scheduled(fixedRate = 300_000)
    public void reconcileAndExpirePendingPurchases() {
        LocalDateTime graceBefore = LocalDateTime.now().minusMinutes(2);
        List<Purchase> candidates = purchaseRepository.findPendingWithConversationBefore(
                graceBefore,
                PageRequest.of(0, 50)
        );
        int activated = 0;
        for (Purchase candidate : candidates) {
            try {
                if (purchaseService.reconcilePaidPendingPurchase(candidate.getId())) {
                    activated++;
                }
            } catch (RuntimeException exception) {
                log.warn(
                        "Pending purchase reconcile failed. purchaseId={} conversationId={} reason={}",
                        candidate.getId(),
                        candidate.getPaymentConversationId(),
                        exception.getMessage()
                );
            }
        }
        if (activated > 0) {
            log.info("Pending purchase reconcile activated {} purchase(s)", activated);
        }
        purchaseService.cancelExpiredPendingPurchases(paymentClientProperties.getPendingTimeoutMinutes());
    }
}
