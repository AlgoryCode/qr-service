package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogScopes;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.enums.FulfillmentStatus;
import com.ael.algoryqrservice.model.enums.MenuPublicAccessDisabledReason;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.repository.PurchaseFulfillmentRepository;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MenuPublicAccessServiceTest {

    @Mock
    private EntitlementService entitlementService;
    @Mock
    private PurchaseRepository purchaseRepository;
    @Mock
    private PurchaseFulfillmentRepository purchaseFulfillmentRepository;
    @Mock
    private MenuRepository menuRepository;

    private MenuPublicAccessService service;

    @BeforeEach
    void setUp() {
        service = new MenuPublicAccessService(
                entitlementService,
                purchaseRepository,
                purchaseFulfillmentRepository,
                menuRepository
        );
    }

    @Test
    void evaluate_whenNoMenuScope_thenPackageInactive() {
        when(entitlementService.hasScope(7L, CatalogScopes.QR_MENU_OWNER)).thenReturn(false);

        MenuPublicAccessService.AccessDecision decision = service.evaluate(7L);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).isEqualTo(MenuPublicAccessDisabledReason.PACKAGE_INACTIVE);
    }

    @Test
    void evaluate_whenOverdueInstallment_thenInstallmentOverdue() {
        Purchase purchase = Purchase.builder()
                .id(11L)
                .userId(7L)
                .status(PurchaseStatus.ACTIVE)
                .startsAt(LocalDateTime.now().minusDays(1))
                .expiresAt(LocalDateTime.now().plusDays(10))
                .build();
        when(entitlementService.hasScope(7L, CatalogScopes.QR_MENU_OWNER)).thenReturn(true);
        when(purchaseRepository.findByUserIdAndStatus(7L, PurchaseStatus.ACTIVE)).thenReturn(List.of(purchase));
        when(purchaseFulfillmentRepository.existsByPurchaseIdInAndStatus(List.of(11L), FulfillmentStatus.OVERDUE))
                .thenReturn(true);

        MenuPublicAccessService.AccessDecision decision = service.evaluate(7L);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).isEqualTo(MenuPublicAccessDisabledReason.INSTALLMENT_OVERDUE);
    }

    @Test
    void evaluate_whenScopeAndNoOverdue_thenAllow() {
        Purchase purchase = Purchase.builder()
                .id(11L)
                .userId(7L)
                .status(PurchaseStatus.ACTIVE)
                .startsAt(LocalDateTime.now().minusDays(1))
                .expiresAt(LocalDateTime.now().plusDays(10))
                .build();
        when(entitlementService.hasScope(7L, CatalogScopes.QR_MENU_OWNER)).thenReturn(true);
        when(purchaseRepository.findByUserIdAndStatus(7L, PurchaseStatus.ACTIVE)).thenReturn(List.of(purchase));
        when(purchaseFulfillmentRepository.existsByPurchaseIdInAndStatus(List.of(11L), FulfillmentStatus.OVERDUE))
                .thenReturn(false);

        MenuPublicAccessService.AccessDecision decision = service.evaluate(7L);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.reason()).isNull();
    }

    @Test
    void syncForUser_whenAllowed_thenEnableMenus() {
        Purchase purchase = Purchase.builder()
                .id(11L)
                .userId(7L)
                .status(PurchaseStatus.ACTIVE)
                .startsAt(LocalDateTime.now().minusDays(1))
                .expiresAt(LocalDateTime.now().plusDays(10))
                .build();
        when(entitlementService.hasScope(7L, CatalogScopes.QR_MENU_OWNER)).thenReturn(true);
        when(purchaseRepository.findByUserIdAndStatus(7L, PurchaseStatus.ACTIVE)).thenReturn(List.of(purchase));
        when(purchaseFulfillmentRepository.existsByPurchaseIdInAndStatus(List.of(11L), FulfillmentStatus.OVERDUE))
                .thenReturn(false);

        service.syncForUser(7L);

        verify(menuRepository).updatePublicAccessByUserId(7L, true, null);
    }

    @Test
    void syncForUser_whenNullUser_thenSkip() {
        service.syncForUser(null);
        verify(menuRepository, never()).updatePublicAccessByUserId(any(), anyBoolean(), any());
        verify(menuRepository, never()).updatePublicAccessByUserId(any(), anyBoolean(), isNull());
    }

    @Test
    void syncForUser_whenPackageInactive_thenDisableWithReason() {
        when(entitlementService.hasScope(7L, CatalogScopes.QR_MENU_OWNER)).thenReturn(false);

        service.syncForUser(7L);

        verify(menuRepository).updatePublicAccessByUserId(
                eq(7L),
                eq(false),
                eq(MenuPublicAccessDisabledReason.PACKAGE_INACTIVE.name())
        );
    }
}
