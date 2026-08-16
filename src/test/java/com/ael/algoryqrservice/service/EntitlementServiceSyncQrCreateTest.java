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
    void repairUsablePackageEntitlements_whenActiveQrsExist_thenSyncUsedQuantity() {
        Long userId = 7L;
        UserEntitlement entitlement = UserEntitlement.builder()
                .id(1L)
                .userId(userId)
                .productCode(CatalogProducts.QR_CREATE)
                .purchaseId(10L)
                .totalQuantity(100)
                .remainingQuantity(100)
                .usedQuantity(0)
                .unlimited(false)
                .startsAt(LocalDateTime.now().minusDays(1))
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();
        Purchase purchase = Purchase.builder()
                .id(10L)
                .userId(userId)
                .status(PurchaseStatus.ACTIVE)
                .startsAt(LocalDateTime.now().minusDays(1))
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();

        when(purchaseRepository.findByUserIdAndStatusAndExpiresAtBefore(eq(userId), eq(PurchaseStatus.ACTIVE), any()))
                .thenReturn(List.of());
        when(purchaseRepository.findByUserIdAndStatus(userId, PurchaseStatus.ACTIVE)).thenReturn(List.of(purchase));
        when(entitlementRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(entitlement));
        when(entitlementRepository.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of(entitlement));
        when(purchaseRepository.findAllById(List.of(10L))).thenReturn(List.of(purchase));
        when(qrRepository.countByUserIdAndDeletedFalse(userId)).thenReturn(3L);
        when(menuRepository.countActiveLiveMenusForUser(userId)).thenReturn(0L);

        entitlementService.getUserEntitlements(userId);

        ArgumentCaptor<UserEntitlement> captor = ArgumentCaptor.forClass(UserEntitlement.class);
        verify(entitlementRepository).save(captor.capture());
        assertThat(captor.getValue().getUsedQuantity()).isEqualTo(3);
        assertThat(captor.getValue().getRemainingQuantity()).isEqualTo(97);
    }

    @Test
    void syncQrCreateEntitlements_whenLegacyAndCurrentQrsExist_thenCountsAllAgainstActivePurchase() {
        Long userId = 7L;
        UserEntitlement activeEntitlement = UserEntitlement.builder()
                .id(1L)
                .userId(userId)
                .productCode(CatalogProducts.QR_CREATE)
                .purchaseId(10L)
                .totalQuantity(5)
                .remainingQuantity(0)
                .usedQuantity(5)
                .unlimited(false)
                .startsAt(LocalDateTime.now().minusDays(1))
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();
        Purchase activePurchase = Purchase.builder()
                .id(10L)
                .userId(userId)
                .status(PurchaseStatus.ACTIVE)
                .startsAt(LocalDateTime.now().minusDays(1))
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();

        when(entitlementRepository.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of(activeEntitlement));
        when(purchaseRepository.findAllById(List.of(10L))).thenReturn(List.of(activePurchase));
        when(qrRepository.countByUserIdAndDeletedFalse(userId)).thenReturn(5L);

        entitlementService.syncQrCreateEntitlements(userId);

        ArgumentCaptor<UserEntitlement> captor = ArgumentCaptor.forClass(UserEntitlement.class);
        verify(entitlementRepository).save(captor.capture());
        assertThat(captor.getValue().getUsedQuantity()).isEqualTo(5);
        assertThat(captor.getValue().getRemainingQuantity()).isEqualTo(0);
    }

    @Test
    void syncQrCreateEntitlements_whenGlobalQrCountDecreases_thenRestoresActivePurchaseRemaining() {
        Long userId = 7L;
        UserEntitlement activeEntitlement = UserEntitlement.builder()
                .id(1L)
                .userId(userId)
                .productCode(CatalogProducts.QR_CREATE)
                .purchaseId(10L)
                .totalQuantity(5)
                .remainingQuantity(0)
                .usedQuantity(5)
                .unlimited(false)
                .startsAt(LocalDateTime.now().minusDays(1))
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();
        Purchase activePurchase = Purchase.builder()
                .id(10L)
                .userId(userId)
                .status(PurchaseStatus.ACTIVE)
                .startsAt(LocalDateTime.now().minusDays(1))
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();

        when(entitlementRepository.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of(activeEntitlement));
        when(purchaseRepository.findAllById(List.of(10L))).thenReturn(List.of(activePurchase));
        when(qrRepository.countByUserIdAndDeletedFalse(userId)).thenReturn(4L);

        entitlementService.syncQrCreateEntitlements(userId);

        ArgumentCaptor<UserEntitlement> captor = ArgumentCaptor.forClass(UserEntitlement.class);
        verify(entitlementRepository).save(captor.capture());
        assertThat(captor.getValue().getUsedQuantity()).isEqualTo(4);
        assertThat(captor.getValue().getRemainingQuantity()).isEqualTo(1);
    }
}
