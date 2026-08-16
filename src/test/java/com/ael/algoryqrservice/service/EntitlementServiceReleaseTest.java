package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogProducts;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.UserEntitlement;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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

    private EntitlementService entitlementService;

    @BeforeEach
    void setUp() {
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
                userTrialService
        );
    }

    @Test
    void release_whenMenuEntitlementUsed_thenRestoreRemaining() {
        Long userId = 7L;
        UserEntitlement entitlement = UserEntitlement.builder()
                .id(1L)
                .userId(userId)
                .productCode(CatalogProducts.QR_MENU)
                .purchaseId(10L)
                .totalQuantity(5)
                .remainingQuantity(2)
                .usedQuantity(3)
                .unlimited(false)
                .build();
        Purchase purchase = Purchase.builder()
                .id(10L)
                .userId(userId)
                .status(PurchaseStatus.ACTIVE)
                .expiresAt(LocalDateTime.now().plusDays(10))
                .build();

        when(purchaseRepository.findByUserIdAndStatusAndExpiresAtBefore(eq(userId), eq(PurchaseStatus.ACTIVE), any()))
                .thenReturn(List.of());
        when(entitlementRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(entitlement));
        when(purchaseRepository.findAllById(List.of(10L))).thenReturn(List.of(purchase));

        entitlementService.release(userId, CatalogProducts.QR_MENU, 1);

        ArgumentCaptor<UserEntitlement> captor = ArgumentCaptor.forClass(UserEntitlement.class);
        verify(entitlementRepository).save(captor.capture());
        assertThat(captor.getValue().getUsedQuantity()).isEqualTo(2);
        assertThat(captor.getValue().getRemainingQuantity()).isEqualTo(3);
    }
}
