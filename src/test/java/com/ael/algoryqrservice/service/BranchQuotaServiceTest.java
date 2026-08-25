package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogProducts;
import com.ael.algoryqrservice.catalog.CatalogScopes;
import com.ael.algoryqrservice.exception.ForbiddenException;
import com.ael.algoryqrservice.model.dto.BranchDtos;
import com.ael.algoryqrservice.model.enums.FulfillmentReferenceType;
import com.ael.algoryqrservice.repository.BranchRepository;
import com.ael.algoryqrservice.repository.MenuRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BranchQuotaServiceTest {

    @Mock
    private BranchRepository branchRepository;
    @Mock
    private MenuRepository menuRepository;
    @Mock
    private EntitlementService entitlementService;
    @Mock
    private FulfillmentGateService fulfillmentGateService;

    @InjectMocks
    private BranchQuotaService branchQuotaService;

    @Test
    void branchQuota_whenPackageOwnerNoBranches_thenCanCreateOne() {
        when(entitlementService.hasScope(7L, CatalogScopes.QR_MENU_OWNER)).thenReturn(true);
        when(branchRepository.countByUserIdAndDeletedFalse(7L)).thenReturn(0L);
        when(branchRepository.countByUserIdAndGrandfatheredTrueAndDeletedFalse(7L)).thenReturn(0L);
        when(fulfillmentGateService.sumAddonQuantity(7L, CatalogProducts.QR_BRANCH)).thenReturn(0);

        BranchDtos.Quota quota = branchQuotaService.branchQuota(7L);

        assertThat(quota.getAllowed()).isEqualTo(1);
        assertThat(quota.isCanCreate()).isTrue();
    }

    @Test
    void assertCanCreateBranch_whenQuotaExhausted_thenForbidden() {
        when(entitlementService.hasScope(7L, CatalogScopes.QR_MENU_OWNER)).thenReturn(true);
        when(branchRepository.countByUserIdAndDeletedFalse(7L)).thenReturn(1L);
        when(branchRepository.countByUserIdAndGrandfatheredTrueAndDeletedFalse(7L)).thenReturn(0L);
        when(fulfillmentGateService.sumAddonQuantity(7L, CatalogProducts.QR_BRANCH)).thenReturn(0);

        assertThatThrownBy(() -> branchQuotaService.assertCanCreateBranch(7L))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Ek şube ücretlidir");
    }

    @Test
    void assertAndConsumeMenuCreation_whenFirstMenu_thenNoAddonConsume() {
        when(menuRepository.countActiveLiveMenusForBranch(3L)).thenReturn(0L);

        branchQuotaService.assertAndConsumeMenuCreation(7L, 3L);

        verify(entitlementService).requireScope(7L, CatalogScopes.QR_MENU_OWNER);
        verify(fulfillmentGateService, never()).consumeAddon(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void assertAndConsumeMenuCreation_whenSecondMenuWithoutAddon_thenForbidden() {
        when(menuRepository.countActiveLiveMenusForBranch(3L)).thenReturn(1L);
        when(menuRepository.countActiveLiveMenusGroupedByBranch(7L)).thenReturn(List.of());
        when(fulfillmentGateService.sumAddonQuantity(7L, CatalogProducts.QR_MENU)).thenReturn(0);

        assertThatThrownBy(() -> branchQuotaService.assertAndConsumeMenuCreation(7L, 3L))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("ek menü ücretlidir");
    }

    @Test
    void assertAndConsumeMenuCreation_whenSecondMenuWithAddon_thenConsumeFulfillment() {
        when(menuRepository.countActiveLiveMenusForBranch(3L)).thenReturn(1L);
        when(menuRepository.countActiveLiveMenusGroupedByBranch(7L)).thenReturn(List.of());
        when(fulfillmentGateService.sumAddonQuantity(7L, CatalogProducts.QR_MENU)).thenReturn(2);

        branchQuotaService.assertAndConsumeMenuCreation(7L, 3L);

        verify(fulfillmentGateService).consumeAddon(
                7L, CatalogProducts.QR_MENU, 1, FulfillmentReferenceType.MENU, 3L
        );
    }

    @Test
    void menuQuota_whenAddonPurchased_thenExtraAllowed() {
        when(fulfillmentGateService.sumAddonQuantity(7L, CatalogProducts.QR_MENU)).thenReturn(2);
        List<Object[]> grouped = List.<Object[]>of(new Object[]{3L, 1L});
        when(menuRepository.countActiveLiveMenusGroupedByBranch(7L)).thenReturn(grouped);

        BranchDtos.MenuQuota quota = branchQuotaService.menuQuota(7L);

        assertThat(quota.getExtraAllowed()).isEqualTo(2);
        assertThat(quota.getExtraUsed()).isZero();
        assertThat(quota.isCanCreateExtra()).isTrue();
    }
}
