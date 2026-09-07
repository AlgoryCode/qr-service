package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.access.AccessSession;
import com.ael.algoryqrservice.access.SessionAccessService;
import com.ael.algoryqrservice.catalog.CatalogPackages;
import com.ael.algoryqrservice.catalog.CatalogProducts;
import com.ael.algoryqrservice.catalog.CatalogScopes;
import com.ael.algoryqrservice.model.FulfillmentDetail;
import com.ael.algoryqrservice.model.dto.UserAccessProfile;
import com.ael.algoryqrservice.model.enums.AccessDecision;
import com.ael.algoryqrservice.model.enums.FulfillmentDetailSource;
import com.ael.algoryqrservice.repository.FulfillmentDetailRepository;
import com.ael.algoryqrservice.service.entitlement.EntitlementMaintenanceService;
import com.ael.algoryqrservice.service.entitlement.PurchaseExpiryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

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
    private EntitlementMaintenanceService entitlementMaintenanceService;
    @Mock
    private PackageActivationService packageActivationService;
    @Mock
    private SessionAccessService sessionAccessService;

    @InjectMocks
    private UserAccessProfileService service;

    @Test
    void resolve_whenAllowWithOnboardingPackage_thenReturnSortedProductsAndScopes() {
        when(sessionAccessService.resolve(USER_ID)).thenReturn(AccessSession.of(
                AccessDecision.ALLOW,
                CatalogPackages.ULTIMATE_TRIAL_PACKAGE,
                LocalDateTime.now().plusDays(10),
                null
        ));
        when(fulfillmentDetailRepository.findAllActiveByUserId(eq(USER_ID), any(LocalDateTime.class)))
                .thenReturn(List.of(
                        detail(CatalogProducts.QR_MENU, CatalogScopes.QR_MENU_OWNER),
                        detail(CatalogProducts.QR_CREATE, CatalogScopes.QR_CREATE_OWNER)
                ));

        UserAccessProfile profile = service.resolve(USER_ID);

        assertThat(profile.activePackage()).isEqualTo(CatalogPackages.ULTIMATE_TRIAL_PACKAGE);
        assertThat(profile.products()).containsExactly(CatalogProducts.QR_CREATE, CatalogProducts.QR_MENU);
        assertThat(profile.scopes()).containsExactly(CatalogScopes.QR_CREATE_OWNER, CatalogScopes.QR_MENU_OWNER);
        verify(purchaseExpiryService).expireDueForUser(USER_ID);
        verify(packageActivationService).ensureSubscriptionState(USER_ID);
        verify(entitlementMaintenanceService).repairUser(USER_ID);
    }

    @Test
    void resolve_whenNotAllow_thenReturnEmptyProfileWithoutLoadingDetails() {
        when(sessionAccessService.resolve(USER_ID)).thenReturn(AccessSession.of(
                AccessDecision.START_PACKAGE,
                null,
                null,
                null
        ));

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
                .source(FulfillmentDetailSource.ONBOARDING_PACKAGE)
                .quantity(1)
                .usedQuantity(0)
                .unlimited(false)
                .build();
    }
}
