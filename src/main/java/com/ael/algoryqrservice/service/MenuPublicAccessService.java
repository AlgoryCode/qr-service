package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogScopes;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.enums.FulfillmentStatus;
import com.ael.algoryqrservice.model.enums.MenuPublicAccessDisabledReason;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.repository.PurchaseFulfillmentRepository;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MenuPublicAccessService {

    public record AccessDecision(boolean allowed, MenuPublicAccessDisabledReason reason) {
        public static AccessDecision allow() {
            return new AccessDecision(true, null);
        }

        public static AccessDecision deny(MenuPublicAccessDisabledReason reason) {
            return new AccessDecision(false, reason);
        }
    }

    private final EntitlementService entitlementService;
    private final PurchaseRepository purchaseRepository;
    private final PurchaseFulfillmentRepository purchaseFulfillmentRepository;
    private final MenuRepository menuRepository;

    public MenuPublicAccessService(
            @Lazy EntitlementService entitlementService,
            PurchaseRepository purchaseRepository,
            PurchaseFulfillmentRepository purchaseFulfillmentRepository,
            MenuRepository menuRepository
    ) {
        this.entitlementService = entitlementService;
        this.purchaseRepository = purchaseRepository;
        this.purchaseFulfillmentRepository = purchaseFulfillmentRepository;
        this.menuRepository = menuRepository;
    }

    @Transactional(readOnly = true)
    public AccessDecision evaluate(Long userId) {
        if (userId == null) {
            return AccessDecision.deny(MenuPublicAccessDisabledReason.PACKAGE_INACTIVE);
        }
        if (!entitlementService.hasScope(userId, CatalogScopes.QR_MENU_OWNER)) {
            return AccessDecision.deny(MenuPublicAccessDisabledReason.PACKAGE_INACTIVE);
        }

        List<Purchase> activePurchases = purchaseRepository.findByUserIdAndStatus(userId, PurchaseStatus.ACTIVE)
                .stream()
                .filter(Purchase::isUsable)
                .toList();
        if (activePurchases.isEmpty()) {
            return AccessDecision.deny(MenuPublicAccessDisabledReason.PACKAGE_INACTIVE);
        }

        List<Long> purchaseIds = activePurchases.stream().map(Purchase::getId).toList();
        if (purchaseFulfillmentRepository.existsByPurchaseIdInAndStatus(purchaseIds, FulfillmentStatus.OVERDUE)) {
            return AccessDecision.deny(MenuPublicAccessDisabledReason.INSTALLMENT_OVERDUE);
        }
        return AccessDecision.allow();
    }

    @Transactional
    public void deactivateActiveMenusForUser(Long userId) {
        if (userId == null) {
            return;
        }
        menuRepository.deactivateActiveMenusByUserId(userId);
    }

    @Transactional
    public void syncForUser(Long userId) {
        if (userId == null) {
            return;
        }
        AccessDecision decision = evaluate(userId);
        menuRepository.updatePublicAccessByUserId(
                userId,
                decision.allowed(),
                decision.reason() == null ? null : decision.reason().name()
        );
    }

    @Transactional
    public void syncForUsers(Iterable<Long> userIds) {
        if (userIds == null) {
            return;
        }
        for (Long userId : userIds) {
            syncForUser(userId);
        }
    }

    @Transactional
    public void syncAllMenuOwners() {
        syncForUsers(menuRepository.findDistinctUserIdsByDeletedFalse());
    }
}
