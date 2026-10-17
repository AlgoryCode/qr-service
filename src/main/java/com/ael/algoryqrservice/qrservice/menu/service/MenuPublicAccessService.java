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
    public AccessDecision evaluate(Long merchantId) {
        if (merchantId == null) {
            return AccessDecision.deny(MenuPublicAccessDisabledReason.PACKAGE_INACTIVE);
        }
        AccessSession session = sessionAccessService.resolve(merchantId);
        if (!session.isAllow()) {
            return AccessDecision.deny(disabledReason(session.decision()));
        }
        if (menuAccessAllowed(merchantId)) {
            return AccessDecision.allow();
        }
        return AccessDecision.deny(MenuPublicAccessDisabledReason.PACKAGE_INACTIVE);
    }

    private boolean menuAccessAllowed(Long merchantId) {
        try {
            return fulfillmentClient.findProductAccess(merchantId, CatalogProducts.QR_MENU).allowed();
        } catch (FulfillmentUnavailableException exception) {
            return false;
        }
    }

    @Transactional
    public void deactivateActiveMenusForUser(Long merchantId) {
        if (merchantId == null) {
            return;
        }
        menuRepository.deactivateActiveMenusByUserId(merchantId);
    }

    @Transactional
    public void syncForUser(Long merchantId) {
        if (merchantId == null) {
            return;
        }
        AccessDecision decision = evaluate(merchantId);
        menuRepository.updatePublicAccessByUserId(merchantId, decision.allowed(), decision.reason());
    }

    @Transactional
    public void syncForUsers(Iterable<Long> userIds) {
        if (userIds == null) {
            return;
        }
        for (Long merchantId : userIds) {
            syncForUser(merchantId);
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
