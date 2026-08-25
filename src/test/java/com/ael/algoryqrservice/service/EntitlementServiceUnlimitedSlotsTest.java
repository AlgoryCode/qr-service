package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogProducts;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.model.enums.PurchaseType;
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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntitlementServiceUnlimitedSlotsTest {

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
    private FulfillmentGrantService fulfillmentGrantService;
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
        org.mockito.Mockito.lenient().when(fulfillmentGrantServiceProvider.getObject()).thenReturn(fulfillmentGrantService);
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
        org.mockito.Mockito.lenient().when(menuRepository.countActiveLiveMenusGroupedByBranch(any()))
                .thenReturn(List.of());
        org.mockito.Mockito.lenient().when(qrRepository.countByUserIdAndDeletedFalse(any())).thenReturn(0L);
        org.mockito.Mockito.lenient().when(menuProductRepository.countActiveProductsForUser(any())).thenReturn(0L);
    }

    @Test
    void assertMenuProductCreationAllowed_whenFulfillmentRemaining_thenAllowsCreation() {
        Long userId = 13L;
        Purchase purchase = Purchase.builder()
                .id(104L)
                .userId(userId)
                .packageId(4L)
                .purchaseType(PurchaseType.PAID)
                .status(PurchaseStatus.ACTIVE)
                .startsAt(LocalDateTime.now().minusDays(1))
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();

        when(purchaseRepository.findByUserIdAndStatus(userId, PurchaseStatus.ACTIVE)).thenReturn(List.of(purchase));
        when(planPackageRepository.findByIdWithItems(4L)).thenReturn(Optional.empty());
        when(fulfillmentGateService.remainingQuantity(userId, CatalogProducts.MENU_PRODUCT, false))
                .thenReturn(Integer.MAX_VALUE);

        assertThatCode(() -> entitlementService.assertMenuProductCreationAllowed(userId, 1))
                .doesNotThrowAnyException();
    }

    @Test
    void assertMenuActivationAllowed_whenExtraMenuRemaining_thenAllowsActivation() {
        Long userId = 13L;
        Purchase purchase = Purchase.builder()
                .id(104L)
                .userId(userId)
                .packageId(4L)
                .purchaseType(PurchaseType.PAID)
                .status(PurchaseStatus.ACTIVE)
                .startsAt(LocalDateTime.now().minusDays(1))
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();

        when(purchaseRepository.findByUserIdAndStatus(userId, PurchaseStatus.ACTIVE)).thenReturn(List.of(purchase));
        when(planPackageRepository.findByIdWithItems(4L)).thenReturn(Optional.empty());
        when(fulfillmentGateService.remainingQuantity(userId, CatalogProducts.QR_MENU, true))
                .thenReturn(Integer.MAX_VALUE);

        assertThatCode(() -> entitlementService.assertMenuActivationAllowed(userId))
                .doesNotThrowAnyException();
    }
}
