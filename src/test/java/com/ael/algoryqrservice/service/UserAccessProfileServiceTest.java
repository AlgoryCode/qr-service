package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogPackages;
import com.ael.algoryqrservice.catalog.CatalogProducts;
import com.ael.algoryqrservice.catalog.CatalogScopes;
import com.ael.algoryqrservice.model.FulfillmentDetail;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.dto.UserAccessProfile;
import com.ael.algoryqrservice.model.enums.FulfillmentDetailSource;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.repository.FulfillmentDetailRepository;
import com.ael.algoryqrservice.service.entitlement.EntitlementMaintenanceService;
import com.ael.algoryqrservice.service.entitlement.PurchaseExpiryService;
import com.ael.algoryqrservice.service.entitlement.PurchaseSelectionPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAccessProfileServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private FulfillmentDetailRepository fulfillmentDetailRepository;
    @Mock
    private PurchaseExpiryService purchaseExpiryService;
    @Mock
    private PurchaseSelectionPolicy purchaseSelectionPolicy;
    @Mock
    private EntitlementMaintenanceService entitlementMaintenanceService;
    @Mock
    private PackageActivationService packageActivationService;

    @InjectMocks
    private UserAccessProfileService service;

    @Test
    void resolve_whenActiveTrialPurchaseExists_thenReturnSortedProductsAndScopes() {
        Purchase trial = Purchase.builder()
                .id(102L)
                .userId(USER_ID)
                .packageId(4L)
                .packageCode(CatalogPackages.ULTIMATE_PACKAGE)
                .purchaseType(PurchaseType.TRIAL)
                .status(PurchaseStatus.ACTIVE)
                .startsAt(LocalDateTime.now().minusDays(1))
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();

        when(purchaseSelectionPolicy.usablePurchases(USER_ID)).thenReturn(List.of(trial));
        when(purchaseSelectionPolicy.highestPriority(List.of(trial))).thenReturn(Optional.of(trial));
        when(fulfillmentDetailRepository.findAllActiveByUserId(eq(USER_ID), any(LocalDateTime.class)))
                .thenReturn(List.of(
                        detail(CatalogProducts.QR_MENU, CatalogScopes.QR_MENU_OWNER),
                        detail(CatalogProducts.QR_CREATE, CatalogScopes.QR_CREATE_OWNER)
                ));

        UserAccessProfile profile = service.resolve(USER_ID);

        assertThat(profile.activePackage()).isEqualTo(CatalogPackages.ULTIMATE_PACKAGE);
        assertThat(profile.products()).containsExactly(CatalogProducts.QR_CREATE, CatalogProducts.QR_MENU);
        assertThat(profile.scopes()).containsExactly(CatalogScopes.QR_CREATE_OWNER, CatalogScopes.QR_MENU_OWNER);
        verify(purchaseExpiryService).expireDueForUser(USER_ID);
        verify(packageActivationService).ensureSubscriptionState(USER_ID);
        verify(entitlementMaintenanceService).repairUser(USER_ID);
    }

    @Test
    void resolve_whenNoActivePurchase_thenReturnEmptyProfileWithoutLoadingDetails() {
        when(purchaseSelectionPolicy.usablePurchases(USER_ID)).thenReturn(List.of());
        when(purchaseSelectionPolicy.highestPriority(List.of())).thenReturn(Optional.empty());

        UserAccessProfile profile = service.resolve(USER_ID);

        assertThat(profile.activePackage()).isNull();
        assertThat(profile.products()).isEmpty();
        assertThat(profile.scopes()).isEmpty();
        verify(purchaseExpiryService).expireDueForUser(USER_ID);
        verify(fulfillmentDetailRepository, never()).findAllActiveByUserId(any(), any());
    }

    private static FulfillmentDetail detail(String featureCode, String scopeCode) {
        return FulfillmentDetail.builder()
                .id(1L)
                .fulfillmentId(10L)
                .userId(USER_ID)
                .featureCode(featureCode)
                .scopeCode(scopeCode)
                .source(FulfillmentDetailSource.PACKAGE_INCLUDE)
                .quantity(1)
                .usedQuantity(0)
                .unlimited(false)
                .build();
    }
}
