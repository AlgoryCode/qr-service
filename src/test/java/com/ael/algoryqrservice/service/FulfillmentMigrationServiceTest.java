package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogProducts;
import com.ael.algoryqrservice.catalog.CatalogScopes;
import com.ael.algoryqrservice.model.FulfillmentDetail;
import com.ael.algoryqrservice.model.GrantFulfillment;
import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.PlanPackageItem;
import com.ael.algoryqrservice.model.Product;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.UserEntitlement;
import com.ael.algoryqrservice.model.enums.FulfillmentDetailSource;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.repository.FulfillmentDetailRepository;
import com.ael.algoryqrservice.repository.GrantFulfillmentRepository;
import com.ael.algoryqrservice.repository.PlanPackageRepository;
import com.ael.algoryqrservice.repository.ProductRepository;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.repository.UserEntitlementRepository;
import com.ael.algoryqrservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FulfillmentMigrationServiceTest {

    @Mock
    private PurchaseRepository purchaseRepository;
    @Mock
    private UserEntitlementRepository userEntitlementRepository;
    @Mock
    private GrantFulfillmentRepository grantFulfillmentRepository;
    @Mock
    private FulfillmentDetailRepository fulfillmentDetailRepository;
    @Mock
    private PlanPackageRepository planPackageRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private UserRepository userRepository;

    private FulfillmentMigrationService service;

    @BeforeEach
    void setUp() {
        service = new FulfillmentMigrationService(
                purchaseRepository,
                userEntitlementRepository,
                grantFulfillmentRepository,
                fulfillmentDetailRepository,
                planPackageRepository,
                productRepository,
                userRepository
        );
    }

    @Test
    void backfillUser_whenPackageGrantMissing_thenCopyUsedQuantityFromEntitlement() {
        Purchase purchase = usablePurchase();
        Product product = Product.builder()
                .id(3L)
                .code(CatalogProducts.QR_CREATE)
                .name("QR")
                .scopeCode(CatalogScopes.QR_CREATE_OWNER)
                .build();
        PlanPackage planPackage = PlanPackage.builder()
                .id(4L)
                .items(List.of(PlanPackageItem.builder().product(product).quantity(5).unlimited(false).build()))
                .build();
        UserEntitlement entitlement = UserEntitlement.builder()
                .productId(3L)
                .productCode(CatalogProducts.QR_CREATE)
                .purchaseId(333L)
                .totalQuantity(5)
                .usedQuantity(2)
                .unlimited(false)
                .build();

        when(purchaseRepository.findByUserIdAndStatus(22L, PurchaseStatus.ACTIVE)).thenReturn(List.of(purchase));
        when(grantFulfillmentRepository.findByPurchaseId(333L)).thenReturn(Optional.empty());
        when(planPackageRepository.findByIdWithItems(4L)).thenReturn(Optional.of(planPackage));
        when(grantFulfillmentRepository.save(any(GrantFulfillment.class))).thenAnswer(invocation -> {
            GrantFulfillment grant = invocation.getArgument(0);
            grant.setId(11L);
            return grant;
        });
        when(userEntitlementRepository.findByPurchaseIdOrderByProductCodeAsc(333L)).thenReturn(List.of(entitlement));

        FulfillmentMigrationService.MigrationResult result = service.backfillUser(22L);

        assertThat(result.fulfillmentCount()).isEqualTo(1);
        ArgumentCaptor<FulfillmentDetail> captor = ArgumentCaptor.forClass(FulfillmentDetail.class);
        verify(fulfillmentDetailRepository).save(captor.capture());
        assertThat(captor.getValue().getFeatureCode()).isEqualTo(CatalogProducts.QR_CREATE);
        assertThat(captor.getValue().getUsedQuantity()).isEqualTo(2);
        assertThat(captor.getValue().getQuantity()).isEqualTo(5);
        assertThat(captor.getValue().getScopeCode()).isEqualTo(CatalogScopes.QR_CREATE_OWNER);
        assertThat(captor.getValue().getSource()).isEqualTo(FulfillmentDetailSource.PACKAGE_INCLUDE);
    }

    @Test
    void backfillUser_whenGrantAlreadyHasDetails_thenDoNotOverwriteUsedQuantity() {
        Purchase purchase = usablePurchase();
        GrantFulfillment existing = GrantFulfillment.builder().id(11L).purchaseId(333L).userId(22L).packageId(4L).build();
        FulfillmentDetail detail = FulfillmentDetail.builder()
                .id(21L)
                .fulfillmentId(11L)
                .featureCode(CatalogProducts.QR_CREATE)
                .usedQuantity(1)
                .quantity(5)
                .build();

        when(purchaseRepository.findByUserIdAndStatus(22L, PurchaseStatus.ACTIVE)).thenReturn(List.of(purchase));
        when(grantFulfillmentRepository.findByPurchaseId(333L)).thenReturn(Optional.of(existing));
        when(fulfillmentDetailRepository.findByFulfillmentId(11L)).thenReturn(List.of(detail));

        FulfillmentMigrationService.MigrationResult result = service.backfillUser(22L);

        assertThat(result.fulfillmentCount()).isZero();
        verify(fulfillmentDetailRepository, never()).save(any());
        verify(grantFulfillmentRepository, never()).save(any());
    }

    private static Purchase usablePurchase() {
        return Purchase.builder()
                .id(333L)
                .userId(22L)
                .packageId(4L)
                .purchaseType(PurchaseType.PAID)
                .status(PurchaseStatus.ACTIVE)
                .startsAt(LocalDateTime.now().minusDays(1))
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();
    }
}
