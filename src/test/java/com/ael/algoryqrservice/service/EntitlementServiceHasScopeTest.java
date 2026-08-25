package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogScopes;
import com.ael.algoryqrservice.repository.FulfillmentDetailRepository;
import com.ael.algoryqrservice.repository.FulfillmentUsageLogRepository;
import com.ael.algoryqrservice.repository.GrantFulfillmentRepository;
import com.ael.algoryqrservice.repository.MenuProductRepository;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.repository.PlanPackageRepository;
import com.ael.algoryqrservice.repository.ProductRepository;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.repository.QrRepository;
import com.ael.algoryqrservice.repository.UserEntitlementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntitlementServiceHasScopeTest {

    @Mock
    private UserEntitlementRepository entitlementRepository;
    @Mock
    private PurchaseRepository purchaseRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private PlanPackageRepository planPackageRepository;
    @Mock
    private PurchaseLogService purchaseLogService;
    @Mock
    private MenuPublicAccessService menuPublicAccessService;
    @Mock
    private MenuRepository menuRepository;
    @Mock
    private MenuProductRepository menuProductRepository;
    @Mock
    private QrRepository qrRepository;
    @Mock
    private ObjectProvider<PackageActivationService> packageActivationService;
    @Mock
    private UserTrialService userTrialService;
    @Mock
    private ObjectProvider<FulfillmentGateService> fulfillmentGateServiceProvider;
    @Mock
    private ObjectProvider<FulfillmentGrantService> fulfillmentGrantServiceProvider;
    @Mock
    private ObjectProvider<FulfillmentMigrationService> fulfillmentMigrationServiceProvider;
    @Mock
    private FulfillmentGateService fulfillmentGateService;
    @Mock
    private FulfillmentMigrationService fulfillmentMigrationService;
    @Mock
    private FulfillmentDetailRepository fulfillmentDetailRepository;
    @Mock
    private GrantFulfillmentRepository grantFulfillmentRepository;
    @Mock
    private FulfillmentUsageLogRepository fulfillmentUsageLogRepository;

    private EntitlementService entitlementService;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(fulfillmentGateServiceProvider.getObject()).thenReturn(fulfillmentGateService);
        org.mockito.Mockito.lenient().when(fulfillmentMigrationServiceProvider.getObject()).thenReturn(fulfillmentMigrationService);
        entitlementService = new EntitlementService(
                entitlementRepository,
                purchaseRepository,
                productRepository,
                planPackageRepository,
                purchaseLogService,
                menuPublicAccessService,
                menuRepository,
                menuProductRepository,
                qrRepository,
                packageActivationService,
                userTrialService,
                fulfillmentGateServiceProvider,
                fulfillmentGrantServiceProvider,
                fulfillmentMigrationServiceProvider,
                fulfillmentDetailRepository,
                grantFulfillmentRepository,
                fulfillmentUsageLogRepository
        );
    }

    @Test
    void hasScope_whenFulfillmentAllows_thenReturnTrueWithoutRepair() {
        when(fulfillmentGateService.hasScope(22L, CatalogScopes.QR_MENU_OWNER)).thenReturn(true);

        assertThat(entitlementService.hasScope(22L, CatalogScopes.QR_MENU_OWNER)).isTrue();

        verify(fulfillmentMigrationService, never()).backfillUser(22L);
        verify(purchaseRepository, never()).findByUserIdAndStatus(any(), any());
    }
}
