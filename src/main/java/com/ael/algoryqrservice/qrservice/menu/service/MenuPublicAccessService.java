package com.ael.algoryqrservice.qrservice.menu.service;

import com.ael.algoryqrservice.qrservice.access.AccessSession;
import com.ael.algoryqrservice.qrservice.access.SessionAccessService;
import com.ael.algoryqrservice.qrservice.product.catalog.CatalogProducts;
import com.ael.algoryqrservice.qrservice.client.FulfillmentServiceClient;
import com.ael.algoryqrservice.qrservice.exception.FulfillmentUnavailableException;
import com.ael.algoryqrservice.qrservice.model.enums.MenuPublicAccessDisabledReason;
import com.ael.algoryqrservice.qrservice.repository.MenuRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ael.algoryqrservice.qrservice.model.enums.AccessDecision;

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
    private final FulfillmentServiceClient fulfillmentClient;
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
        if (menuAccessAllowed(userId)) {
            return AccessDecision.allow();
        }
        return AccessDecision.deny(MenuPublicAccessDisabledReason.PACKAGE_INACTIVE);
    }

    private boolean menuAccessAllowed(Long userId) {
        try {
            return fulfillmentClient.findProductAccess(userId, CatalogProducts.QR_MENU).allowed();
        } catch (FulfillmentUnavailableException exception) {
            return false;
        }
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

    private static MenuPublicAccessDisabledReason disabledReason(
            com.ael.algoryqrservice.qrservice.model.enums.AccessDecision decision
    ) {
        return switch (decision) {
            case REQUIRE_PAYMENT -> MenuPublicAccessDisabledReason.INSTALLMENT_OVERDUE;
            case START_PACKAGE, REQUIRE_PURCHASE, ALLOW -> MenuPublicAccessDisabledReason.PACKAGE_INACTIVE;
        };
    }
}
