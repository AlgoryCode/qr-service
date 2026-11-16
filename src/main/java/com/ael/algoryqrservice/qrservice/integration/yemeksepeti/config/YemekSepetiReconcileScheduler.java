package com.ael.algoryqrservice.qrservice.integration.yemeksepeti.config;

import com.ael.algoryqrservice.shared.scheduling.SchedulerLock;
import java.time.Duration;
import com.ael.algoryqrservice.qrservice.integration.service.yemeksepeti.YemekSepetiOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class YemekSepetiReconcileScheduler {

    private final YemekSepetiOrderService orderService;
    private final SchedulerLock schedulerLock;

    @Scheduled(fixedRate = 180_000)
    public void pollOrders() {
        schedulerLock.runExclusively("yemeksepeti-reconcile", Duration.ofMinutes(3), this::pollOrdersLocked);
    }

    void pollOrdersLocked() {
        orderService.reconcileConnected();
    }
}
