package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogProducts;
import com.ael.algoryqrservice.catalog.CatalogScopes;
import com.ael.algoryqrservice.exception.ForbiddenException;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.UserEntitlement;
import com.ael.algoryqrservice.model.dto.BranchDtos;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.repository.BranchRepository;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.repository.UserEntitlementRepository;
import com.ael.algoryqrservice.util.AppTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BranchQuotaServiceTest {

    @Mock
    private BranchRepository branchRepository;
    @Mock
    private MenuRepository menuRepository;
    @Mock
    private UserEntitlementRepository entitlementRepository;
    @Mock
    private PurchaseRepository purchaseRepository;
    @Mock
    private EntitlementService entitlementService;

    @InjectMocks
    private BranchQuotaService branchQuotaService;

    @Test
    void branchQuota_whenPackageOwnerNoBranches_thenCanCreateOne() {
        when(entitlementService.hasScope(7L, CatalogScopes.QR_MENU_OWNER)).thenReturn(true);
        when(branchRepository.countByUserIdAndDeletedFalse(7L)).thenReturn(0L);
        when(branchRepository.countByUserIdAndGrandfatheredTrueAndDeletedFalse(7L)).thenReturn(0L);
        when(entitlementRepository.findByUserIdOrderByCreatedAtDesc(7L)).thenReturn(List.of());

        BranchDtos.Quota quota = branchQuotaService.branchQuota(7L);

        assertThat(quota.getAllowed()).isEqualTo(1);
        assertThat(quota.isCanCreate()).isTrue();
    }

    @Test
    void assertCanCreateBranch_whenQuotaExhausted_thenForbidden() {
        when(entitlementService.hasScope(7L, CatalogScopes.QR_MENU_OWNER)).thenReturn(true);
        when(branchRepository.countByUserIdAndDeletedFalse(7L)).thenReturn(1L);
        when(branchRepository.countByUserIdAndGrandfatheredTrueAndDeletedFalse(7L)).thenReturn(0L);
        when(entitlementRepository.findByUserIdOrderByCreatedAtDesc(7L)).thenReturn(List.of());

        assertThatThrownBy(() -> branchQuotaService.assertCanCreateBranch(7L))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Ek şube ücretlidir");
    }

    @Test
    void assertAndConsumeMenuCreation_whenFirstMenu_thenNoAddonConsume() {
        when(menuRepository.countActiveLiveMenusForBranch(3L)).thenReturn(0L);

        branchQuotaService.assertAndConsumeMenuCreation(7L, 3L);

        verify(entitlementService).requireScope(7L, CatalogScopes.QR_MENU_OWNER);
    }

    @Test
    void assertAndConsumeMenuCreation_whenSecondMenuWithoutAddon_thenForbidden() {
        when(menuRepository.countActiveLiveMenusForBranch(3L)).thenReturn(1L);
        when(entitlementRepository.findByUserIdOrderByCreatedAtDesc(7L)).thenReturn(List.of());

        assertThatThrownBy(() -> branchQuotaService.assertAndConsumeMenuCreation(7L, 3L))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("ek menü ücretlidir");
    }

    @Test
    void menuQuota_whenAddonPurchased_thenExtraAllowed() {
        LocalDateTime now = AppTime.nowLocal();
        Purchase purchase = Purchase.builder()
                .id(11L)
                .purchaseType(PurchaseType.ADD_ON)
                .status(PurchaseStatus.ACTIVE)
                .startsAt(now.minusDays(1))
                .expiresAt(now.plusDays(10))
                .build();
        UserEntitlement entitlement = UserEntitlement.builder()
                .productCode(CatalogProducts.QR_MENU)
                .purchaseId(11L)
                .totalQuantity(2)
                .remainingQuantity(2)
                .usedQuantity(0)
                .startsAt(now.minusDays(1))
                .expiresAt(now.plusDays(10))
                .build();
        when(entitlementRepository.findByUserIdOrderByCreatedAtDesc(7L)).thenReturn(List.of(entitlement));
        when(purchaseRepository.findAllById(List.of(11L))).thenReturn(List.of(purchase));
        List<Object[]> grouped = List.<Object[]>of(new Object[]{3L, 1L});
        when(menuRepository.countActiveLiveMenusGroupedByBranch(7L)).thenReturn(grouped);

        BranchDtos.MenuQuota quota = branchQuotaService.menuQuota(7L);

        assertThat(quota.getExtraAllowed()).isEqualTo(2);
        assertThat(quota.getExtraUsed()).isZero();
        assertThat(quota.isCanCreateExtra()).isTrue();
    }
}
