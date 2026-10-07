package com.ael.algoryqrservice.event;

import com.ael.algoryqrservice.service.MenuPublicAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PurchasesExpiredListener {

    private final MenuPublicAccessService menuPublicAccessService;

    @EventListener
    public void onPurchasesExpired(PurchasesExpiredEvent event) {
        menuPublicAccessService.syncForUsers(event.userIds());
    }
}
