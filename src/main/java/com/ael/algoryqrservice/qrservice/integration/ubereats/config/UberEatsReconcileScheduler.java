package com.ael.algoryqrservice.qrservice.integration.ubereats.config;

import com.ael.algoryqrservice.qrservice.integration.service.ubereats.UberEatsOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UberEatsReconcileScheduler {

    private final UberEatsOrderService orderService;

    @Scheduled(fixedRate = 180_000)
    public void pollOrders() {
        orderService.reconcileConnected();
    }
}
