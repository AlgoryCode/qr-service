package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogProducts;
import com.ael.algoryqrservice.model.Product;
import com.ael.algoryqrservice.model.enums.FulfillmentReferenceType;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntitlementServiceReleaseTest {

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
    private ObjectProvider<PackageActivationService> packageActivationServiceProvider;
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
                packageActivationServiceProvider,
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
    void release_whenMenuFeature_thenReleaseOnFulfillmentGate() {
        Long userId = 7L;
        when(purchaseRepository.findByUserIdAndStatusAndExpiresAtBefore(eq(userId), eq(PurchaseStatus.ACTIVE), any()))
                .thenReturn(List.of());
        when(productRepository.findByCode(CatalogProducts.QR_MENU)).thenReturn(Optional.of(
                Product.builder().id(3L).code(CatalogProducts.QR_MENU).consumable(true).build()
        ));

        entitlementService.release(userId, CatalogProducts.QR_MENU, 1);

        verify(fulfillmentMigrationService).backfillUser(userId);
        verify(fulfillmentGateService).releaseFeature(
                userId, CatalogProducts.QR_MENU, 1, FulfillmentReferenceType.FEATURE, null
        );
    }
}
