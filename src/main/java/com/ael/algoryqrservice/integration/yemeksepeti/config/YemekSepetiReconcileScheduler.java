package com.ael.algoryqrservice.integration.yemeksepeti.config;

import com.ael.algoryqrservice.integration.yemeksepeti.service.YemekSepetiOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class YemekSepetiReconcileScheduler {

    private final YemekSepetiOrderService orderService;

    @Scheduled(fixedRate = 180_000)
    public void pollOrders() {
        orderService.reconcileConnected();
    }
}
