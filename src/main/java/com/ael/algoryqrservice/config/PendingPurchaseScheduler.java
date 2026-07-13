package com.ael.algoryqrservice.config;

import com.ael.algoryqrservice.service.PurchaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PendingPurchaseScheduler {

    private final PurchaseService purchaseService;
    private final PaymentClientProperties paymentClientProperties;

    @Scheduled(fixedRate = 300_000)
    public void cancelExpiredPendingPurchases() {
        purchaseService.cancelExpiredPendingPurchases(paymentClientProperties.getPendingTimeoutMinutes());
    }
}
