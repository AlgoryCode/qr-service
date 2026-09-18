package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.access.AccessSession;
import com.ael.algoryqrservice.access.PackageProductCatalog;
import com.ael.algoryqrservice.access.SessionAccessService;
import com.ael.algoryqrservice.catalog.CatalogPackages;
import com.ael.algoryqrservice.catalog.CatalogProducts;
import com.ael.algoryqrservice.model.enums.AccessDecision;
import com.ael.algoryqrservice.model.enums.MenuPublicAccessDisabledReason;
import com.ael.algoryqrservice.repository.MenuRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

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
    private SessionAccessService sessionAccessService;
    @Mock
    private PackageProductCatalog packageProductCatalog;
    @Mock
    private MenuRepository menuRepository;

    @InjectMocks
    private MenuPublicAccessService service;

    @Test
    void evaluate_whenSessionRequiresPurchase_thenPackageInactive() {
        when(sessionAccessService.resolve(7L)).thenReturn(AccessSession.of(
                AccessDecision.REQUIRE_PURCHASE, null, null, null
        ));

        MenuPublicAccessService.AccessDecision decision = service.evaluate(7L);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).isEqualTo(MenuPublicAccessDisabledReason.PACKAGE_INACTIVE);
    }

    @Test
    void evaluate_whenSessionRequiresPayment_thenInstallmentOverdue() {
        when(sessionAccessService.resolve(7L)).thenReturn(AccessSession.of(
                AccessDecision.REQUIRE_PAYMENT,
                "PRO_PACKAGE",
                LocalDateTime.now().plusDays(10),
                LocalDateTime.now().plusDays(3)
        ));

        MenuPublicAccessService.AccessDecision decision = service.evaluate(7L);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).isEqualTo(MenuPublicAccessDisabledReason.INSTALLMENT_OVERDUE);
    }

    @Test
    void evaluate_whenUltimateTrialAllowsAndContainsQrMenu_thenAllow() {
        when(sessionAccessService.resolve(7L)).thenReturn(AccessSession.of(
                AccessDecision.ALLOW,
                CatalogPackages.ULTIMATE_TRIAL_PACKAGE,
                LocalDateTime.now().plusDays(15),
                null
        ));
        when(packageProductCatalog.containsProduct(CatalogPackages.ULTIMATE_TRIAL_PACKAGE, CatalogProducts.QR_MENU))
                .thenReturn(true);

        MenuPublicAccessService.AccessDecision decision = service.evaluate(7L);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.reason()).isNull();
    }

    @Test
    void evaluate_whenAllowButQrMenuNotInPackage_thenPackageInactive() {
        when(sessionAccessService.resolve(7L)).thenReturn(AccessSession.of(
                AccessDecision.ALLOW,
                CatalogPackages.ULTIMATE_TRIAL_PACKAGE,
                LocalDateTime.now().plusDays(15),
                null
        ));
        when(packageProductCatalog.containsProduct(CatalogPackages.ULTIMATE_TRIAL_PACKAGE, CatalogProducts.QR_MENU))
                .thenReturn(false);

        MenuPublicAccessService.AccessDecision decision = service.evaluate(7L);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).isEqualTo(MenuPublicAccessDisabledReason.PACKAGE_INACTIVE);
    }

    @Test
    void evaluate_whenPaidPackageAllows_thenAllow() {
        when(sessionAccessService.resolve(7L)).thenReturn(AccessSession.of(
                AccessDecision.ALLOW,
                "PRO_PACKAGE",
                LocalDateTime.now().plusDays(20),
                null
        ));
        when(packageProductCatalog.containsProduct("PRO_PACKAGE", CatalogProducts.QR_MENU)).thenReturn(true);

        MenuPublicAccessService.AccessDecision decision = service.evaluate(7L);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.reason()).isNull();
    }

    @Test
    void syncForUser_whenAllowed_thenEnableMenus() {
        when(sessionAccessService.resolve(7L)).thenReturn(AccessSession.of(
                AccessDecision.ALLOW,
                CatalogPackages.ULTIMATE_TRIAL_PACKAGE,
                LocalDateTime.now().plusDays(15),
                null
        ));
        when(packageProductCatalog.containsProduct(CatalogPackages.ULTIMATE_TRIAL_PACKAGE, CatalogProducts.QR_MENU))
                .thenReturn(true);

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
        when(sessionAccessService.resolve(7L)).thenReturn(AccessSession.of(
                AccessDecision.REQUIRE_PURCHASE, null, null, null
        ));

        service.syncForUser(7L);

        verify(menuRepository).updatePublicAccessByUserId(
                eq(7L),
                eq(false),
                eq(MenuPublicAccessDisabledReason.PACKAGE_INACTIVE)
        );
    }
}
