package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.access.AccessSession;
import com.ael.algoryqrservice.access.PackageProductCatalog;
import com.ael.algoryqrservice.access.SessionAccessService;
import com.ael.algoryqrservice.catalog.CatalogProducts;
import com.ael.algoryqrservice.model.enums.MenuPublicAccessDisabledReason;
import com.ael.algoryqrservice.repository.MenuRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MenuPublicAccessService {

    public record AccessDecision(boolean allowed, MenuPublicAccessDisabledReason reason) {
        public static AccessDecision allow() {
            return new AccessDecision(true, null);
        }

        public static AccessDecision deny(MenuPublicAccessDisabledReason reason) {
            return new AccessDecision(false, reason);
        }
    }

    private final SessionAccessService sessionAccessService;
    private final PackageProductCatalog packageProductCatalog;
    private final MenuRepository menuRepository;

    @Transactional
    public AccessDecision evaluate(Long userId) {
        if (userId == null) {
            return AccessDecision.deny(MenuPublicAccessDisabledReason.PACKAGE_INACTIVE);
        }
        AccessSession session = sessionAccessService.resolve(userId);
        if (!session.isAllow()) {
            return AccessDecision.deny(disabledReason(session.decision()));
        }
        if (!packageProductCatalog.containsProduct(session.packageCode(), CatalogProducts.QR_MENU)) {
            return AccessDecision.deny(MenuPublicAccessDisabledReason.PACKAGE_INACTIVE);
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
        menuRepository.updatePublicAccessByUserId(userId, decision.allowed(), decision.reason());
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

    private static MenuPublicAccessDisabledReason disabledReason(
            com.ael.algoryqrservice.model.enums.AccessDecision decision
    ) {
        return switch (decision) {
            case REQUIRE_PAYMENT -> MenuPublicAccessDisabledReason.INSTALLMENT_OVERDUE;
            case START_PACKAGE, REQUIRE_PURCHASE, ALLOW -> MenuPublicAccessDisabledReason.PACKAGE_INACTIVE;
        };
    }
}
