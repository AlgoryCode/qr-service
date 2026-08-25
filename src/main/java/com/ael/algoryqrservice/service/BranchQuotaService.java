package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogProducts;
import com.ael.algoryqrservice.catalog.CatalogScopes;
import com.ael.algoryqrservice.exception.ForbiddenException;
import com.ael.algoryqrservice.model.dto.BranchDtos;
import com.ael.algoryqrservice.model.enums.FulfillmentReferenceType;
import com.ael.algoryqrservice.repository.BranchRepository;
import com.ael.algoryqrservice.repository.MenuRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BranchQuotaService {

    public static final String EXTRA_BRANCH_REQUIRED = "EXTRA_BRANCH_REQUIRED";
    public static final String EXTRA_MENU_REQUIRED = "EXTRA_MENU_REQUIRED";

    private final BranchRepository branchRepository;
    private final MenuRepository menuRepository;
    private final EntitlementService entitlementService;
    private final FulfillmentGateService fulfillmentGateService;

    @Transactional(readOnly = true)
    public BranchDtos.Quota branchQuota(Long userId) {
        entitlementService.expireDuePurchasesForUser(userId);
        int used = (int) branchRepository.countByUserIdAndDeletedFalse(userId);
        int grandfathered = (int) branchRepository.countByUserIdAndGrandfatheredTrueAndDeletedFalse(userId);
        int extraPurchased = sumAddonQuantity(userId, CatalogProducts.QR_BRANCH);
        int allowed = Math.max(1, grandfathered) + extraPurchased;
        if (!entitlementService.hasScope(userId, CatalogScopes.QR_MENU_OWNER)) {
            allowed = grandfathered;
        }
        int remaining = Math.max(0, allowed - used);
        return BranchDtos.Quota.builder()
                .used(used)
                .allowed(allowed)
                .remaining(remaining)
                .grandfathered(grandfathered)
                .extraPurchased(extraPurchased)
                .canCreate(remaining > 0 && entitlementService.hasScope(userId, CatalogScopes.QR_MENU_OWNER))
                .build();
    }

    @Transactional(readOnly = true)
    public BranchDtos.MenuQuota menuQuota(Long userId) {
        entitlementService.expireDuePurchasesForUser(userId);
        int extraUsed = countExtraMenus(userId);
        int extraAllowed = sumAddonQuantity(userId, CatalogProducts.QR_MENU);
        int extraRemaining = Math.max(0, extraAllowed - extraUsed);
        return BranchDtos.MenuQuota.builder()
                .extraUsed(extraUsed)
                .extraAllowed(extraAllowed)
                .extraRemaining(extraRemaining)
                .canCreateExtra(extraRemaining > 0)
                .build();
    }

    @Transactional
    public void assertCanCreateBranch(Long userId) {
        entitlementService.requireScope(userId, CatalogScopes.QR_MENU_OWNER);
        BranchDtos.Quota quota = branchQuota(userId);
        if (!quota.isCanCreate()) {
            throw new ForbiddenException(
                    EXTRA_BRANCH_REQUIRED,
                    "Ek şube ücretlidir. Lütfen ek şube hakkı satın alın."
            );
        }
    }

    @Transactional
    public void assertAndConsumeMenuCreation(Long userId, Long branchId) {
        entitlementService.requireScope(userId, CatalogScopes.QR_MENU_OWNER);
        long activeOnBranch = menuRepository.countActiveLiveMenusForBranch(branchId);
        if (activeOnBranch < 1) {
            return;
        }
        if (menuQuota(userId).getExtraRemaining() <= 0) {
            throw new ForbiddenException(
                    EXTRA_MENU_REQUIRED,
                    "Bu şubede ek menü ücretlidir. Lütfen ek menü hakkı satın alın."
            );
        }
        fulfillmentGateService.consumeAddon(userId, CatalogProducts.QR_MENU, 1,
                FulfillmentReferenceType.MENU, branchId);
    }

    @Transactional
    public void assertMenuActivationAllowed(Long userId, Long branchId) {
        entitlementService.requireScope(userId, CatalogScopes.QR_MENU_OWNER);
        long activeOnBranch = menuRepository.countActiveLiveMenusForBranch(branchId);
        if (activeOnBranch < 1) {
            return;
        }
        if (menuQuota(userId).getExtraRemaining() <= 0) {
            throw new ForbiddenException(
                    EXTRA_MENU_REQUIRED,
                    "Bu şubede ek menü hakkınız yok. Başka bir menüyü pasif yapın veya ek menü satın alın."
            );
        }
    }

    @Transactional
    public void releaseExtraMenuIfNeeded(Long userId, Long branchId) {
        if (branchId == null) {
            return;
        }
        long remainingActive = menuRepository.countActiveLiveMenusForBranch(branchId);
        if (remainingActive >= 1) {
            fulfillmentGateService.releaseAddon(userId, CatalogProducts.QR_MENU, 1,
                    FulfillmentReferenceType.MENU, branchId);
        }
    }

    public int countExtraMenus(Long userId) {
        List<Object[]> rows = menuRepository.countActiveLiveMenusGroupedByBranch(userId);
        int extra = 0;
        for (Object[] row : rows) {
            long count = ((Number) row[1]).longValue();
            extra += (int) Math.max(0, count - 1);
        }
        return extra;
    }

    private int sumAddonQuantity(Long userId, String featureCode) {
        return fulfillmentGateService.sumAddonQuantity(userId, featureCode);
    }
}
