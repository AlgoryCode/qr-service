package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogProducts;
import com.ael.algoryqrservice.model.FulfillmentDetail;
import com.ael.algoryqrservice.model.GrantFulfillment;
import com.ael.algoryqrservice.model.Product;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.enums.FulfillmentDetailSource;
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
import org.springframework.data.domain.PageImpl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntitlementServiceSyncQrCreateTest {

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
    void getUserEntitlements_whenFulfillmentDetailsExist_thenMapFromFulfillment() {
        Long userId = 7L;
        Purchase purchase = Purchase.builder()
                .id(10L)
                .userId(userId)
                .packageId(4L)
                .purchaseType(PurchaseType.PAID)
                .status(PurchaseStatus.ACTIVE)
                .startsAt(LocalDateTime.now().minusDays(1))
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();
        FulfillmentDetail detail = FulfillmentDetail.builder()
                .id(21L)
                .fulfillmentId(11L)
                .userId(userId)
                .productId(3L)
                .featureCode(CatalogProducts.QR_CREATE)
                .quantity(100)
                .usedQuantity(3)
                .unlimited(false)
                .source(FulfillmentDetailSource.PACKAGE_INCLUDE)
                .startsAt(purchase.getStartsAt())
                .expiresAt(purchase.getExpiresAt())
                .build();
        GrantFulfillment grant = GrantFulfillment.builder()
                .id(11L)
                .userId(userId)
                .purchaseId(10L)
                .packageId(4L)
                .build();

        when(purchaseRepository.findByUserIdAndStatusAndExpiresAtBefore(eq(userId), eq(PurchaseStatus.ACTIVE), any()))
                .thenReturn(List.of());
        when(purchaseRepository.findByUserIdAndStatus(userId, PurchaseStatus.ACTIVE)).thenReturn(List.of(purchase));
        when(planPackageRepository.findByIdWithItems(4L)).thenReturn(Optional.empty());
        when(menuRepository.countActiveLiveMenusGroupedByBranch(userId)).thenReturn(List.of());
        when(qrRepository.countByUserIdAndDeletedFalse(userId)).thenReturn(3L);
        when(menuProductRepository.countActiveProductsForUser(userId)).thenReturn(0L);
        when(fulfillmentDetailRepository.findAllActiveByUserId(eq(userId), any())).thenReturn(List.of(detail));
        when(grantFulfillmentRepository.findAllById(List.of(11L))).thenReturn(List.of(grant));
        when(purchaseRepository.findAllById(List.of(10L))).thenReturn(List.of(purchase));
        when(fulfillmentUsageLogRepository.findByUserIdOrderByCreatedAtDesc(eq(userId), any()))
                .thenReturn(new PageImpl<>(List.of()));
        when(productRepository.findById(3L)).thenReturn(Optional.of(
                Product.builder().id(3L).code(CatalogProducts.QR_CREATE).name("QR").build()
        ));

        var responses = entitlementService.getUserEntitlements(userId);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().getProductCode()).isEqualTo(CatalogProducts.QR_CREATE);
        assertThat(responses.getFirst().getUsedQuantity()).isEqualTo(3);
        assertThat(responses.getFirst().getRemainingQuantity()).isEqualTo(97);
        verify(fulfillmentGateService).replaceUsedQuantity(userId, CatalogProducts.QR_CREATE, 3, false);
    }

    @Test
    void syncQrCreateEntitlements_whenQrCountIsFive_thenReplaceUsedQuantity() {
        when(qrRepository.countByUserIdAndDeletedFalse(7L)).thenReturn(5L);

        entitlementService.syncQrCreateEntitlements(7L);

        verify(fulfillmentGateService).replaceUsedQuantity(7L, CatalogProducts.QR_CREATE, 5, false);
    }
}
