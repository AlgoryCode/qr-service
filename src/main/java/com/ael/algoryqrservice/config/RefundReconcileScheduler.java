package com.ael.algoryqrservice.config;

import com.ael.algoryqrservice.service.PurchaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RefundReconcileScheduler {

    private final PurchaseService purchaseService;

    @Scheduled(fixedRate = 300_000)
    public void reconcileRefunds() {
        try {
            purchaseService.reconcileNeedsReconcileRefunds();
        } catch (RuntimeException exception) {
            log.warn("Refund reconcile run failed: {}", exception.getMessage());
        }
    }
}
