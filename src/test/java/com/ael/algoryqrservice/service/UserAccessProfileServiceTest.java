package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogPackages;
import com.ael.algoryqrservice.catalog.CatalogProducts;
import com.ael.algoryqrservice.catalog.CatalogScopes;
import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.Product;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.UserEntitlement;
import com.ael.algoryqrservice.model.dto.UserAccessProfile;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.repository.PlanPackageRepository;
import com.ael.algoryqrservice.repository.ProductRepository;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.repository.UserEntitlementRepository;
import com.ael.algoryqrservice.util.AppTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAccessProfileServiceTest {

    @Mock
    private PurchaseRepository purchaseRepository;
    @Mock
    private UserEntitlementRepository entitlementRepository;
    @Mock
    private PlanPackageRepository planPackageRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private EntitlementService entitlementService;
    @Mock
    private PackageActivationService packageActivationService;

    @InjectMocks
    private UserAccessProfileService service;

    @BeforeEach
    void freezeIstanbulClock() {
        AppTime.setClock(Clock.fixed(
                LocalDateTime.of(2026, 8, 15, 12, 59, 0).atZone(AppTime.ZONE).toInstant(),
                AppTime.ZONE
        ));
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @AfterEach
    void resetClock() {
        AppTime.resetClock();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @Test
    void resolve_whenActiveTrialPurchaseExists_thenReturnProductsAndScopes() {
        Purchase trial = Purchase.builder()
                .id(102L)
                .userId(1L)
                .packageId(4L)
                .packageCode(CatalogPackages.ULTIMATE_PACKAGE)
                .purchaseType(PurchaseType.TRIAL)
                .status(PurchaseStatus.ACTIVE)
                .startsAt(LocalDateTime.of(2026, 8, 15, 11, 42, 23))
                .expiresAt(LocalDateTime.of(2026, 9, 14, 11, 42, 23))
                .build();

        UserEntitlement qrMenu = entitlement(177L, CatalogProducts.QR_MENU, 102L);
        UserEntitlement qrCreate = entitlement(181L, CatalogProducts.QR_CREATE, 102L);

        when(purchaseRepository.findByUserIdAndStatus(1L, PurchaseStatus.ACTIVE)).thenReturn(List.of(trial));
        when(planPackageRepository.findAllById(List.of(4L))).thenReturn(List.of(
                PlanPackage.builder().id(4L).code(CatalogPackages.ULTIMATE_PACKAGE).priority(300).build()
        ));
        when(entitlementRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(qrMenu, qrCreate));
        when(productRepository.findByCodeIn(List.of(CatalogProducts.QR_CREATE, CatalogProducts.QR_MENU)))
                .thenReturn(List.of(
                        product(CatalogProducts.QR_CREATE, CatalogScopes.QR_CREATE_OWNER),
                        product(CatalogProducts.QR_MENU, CatalogScopes.QR_MENU_OWNER)
                ));

        UserAccessProfile profile = service.resolve(1L);

        assertThat(profile.activePackage()).isEqualTo(CatalogPackages.ULTIMATE_PACKAGE);
        assertThat(profile.products()).containsExactly(CatalogProducts.QR_CREATE, CatalogProducts.QR_MENU);
        assertThat(profile.scopes()).containsExactly(CatalogScopes.QR_CREATE_OWNER, CatalogScopes.QR_MENU_OWNER);
        verify(entitlementService).expireDuePurchasesForUser(1L);
        verify(packageActivationService).ensureSubscriptionState(1L);
        verify(entitlementService).repairUsablePackageEntitlements(1L);
    }

    @Test
    void resolve_whenTrialNotYetStartedInUtcButStartedInIstanbul_thenReturnEmptyWithoutAppTimeFix() {
        AppTime.setClock(Clock.fixed(
                LocalDateTime.of(2026, 8, 15, 9, 44, 30).atZone(AppTime.ZONE).toInstant(),
                AppTime.ZONE
        ));

        Purchase trial = Purchase.builder()
                .id(102L)
                .userId(1L)
                .packageId(4L)
                .packageCode(CatalogPackages.ULTIMATE_PACKAGE)
                .purchaseType(PurchaseType.TRIAL)
                .status(PurchaseStatus.ACTIVE)
                .startsAt(LocalDateTime.of(2026, 8, 15, 11, 42, 23))
                .expiresAt(LocalDateTime.of(2026, 9, 14, 11, 42, 23))
                .build();

        when(purchaseRepository.findByUserIdAndStatus(1L, PurchaseStatus.ACTIVE)).thenReturn(List.of(trial));

        UserAccessProfile profile = service.resolve(1L);

        assertThat(profile.activePackage()).isNull();
        assertThat(profile.products()).isEmpty();
        assertThat(profile.scopes()).isEmpty();
    }

    @Test
    void resolve_whenNoActivePurchase_thenReturnEmptyProfile() {
        when(purchaseRepository.findByUserIdAndStatus(1L, PurchaseStatus.ACTIVE)).thenReturn(List.of());

        UserAccessProfile profile = service.resolve(1L);

        assertThat(profile.activePackage()).isNull();
        assertThat(profile.products()).isEmpty();
        assertThat(profile.scopes()).isEmpty();
        verify(entitlementService).expireDuePurchasesForUser(1L);
    }

    private static UserEntitlement entitlement(Long id, String productCode, Long purchaseId) {
        return UserEntitlement.builder()
                .id(id)
                .userId(1L)
                .productId(id)
                .productCode(productCode)
                .purchaseId(purchaseId)
                .totalQuantity(1)
                .remainingQuantity(1)
                .usedQuantity(0)
                .unlimited(false)
                .startsAt(LocalDateTime.of(2026, 8, 15, 11, 42, 23))
                .expiresAt(LocalDateTime.of(2026, 9, 14, 11, 42, 23))
                .build();
    }

    private static Product product(String code, String scopeCode) {
        return Product.builder()
                .code(code)
                .name(code)
                .scopeCode(scopeCode)
                .active(true)
                .build();
    }
}
