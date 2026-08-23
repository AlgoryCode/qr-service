package com.ael.algoryqrservice.integration.trendyolgo.config;

import com.ael.algoryqrservice.integration.trendyolgo.service.TrendyolGoOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TrendyolGoReconcileScheduler {

    private final TrendyolGoOrderService orderService;

    @Scheduled(fixedRate = 180_000)
    public void pollOrders() {
        orderService.reconcileConnected();
    }
}
