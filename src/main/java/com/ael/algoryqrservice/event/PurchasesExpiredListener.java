package com.ael.algoryqrservice.event;

import com.ael.algoryqrservice.service.PackageActivationService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Keeps subscription state and public menu access in sync when purchases expire.
 * Listening (instead of calling back) keeps the expiry service free of a dependency cycle.
 */
@Component
@RequiredArgsConstructor
public class PurchasesExpiredListener {

    private final PackageActivationService packageActivationService;

    @EventListener
    public void onPurchasesExpired(PurchasesExpiredEvent event) {
        packageActivationService.syncSubscriptionStateForUsers(event.userIds());
    }
}
