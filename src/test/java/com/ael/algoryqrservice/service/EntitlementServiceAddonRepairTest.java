package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogProducts;
import com.ael.algoryqrservice.model.Product;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.UserEntitlement;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.model.enums.PurchaseType;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntitlementServiceAddonRepairTest {

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
    void repairUsablePackageEntitlements_whenAddonPurchase_thenSkipPackageEnsure() {
        Long userId = 9L;
        Purchase addon = Purchase.builder()
                .id(20L)
                .userId(userId)
                .packageId(4L)
                .packageCode(CatalogProducts.QR_MENU)
                .purchaseType(PurchaseType.ADD_ON)
                .installmentCount(2)
                .status(PurchaseStatus.ACTIVE)
                .startsAt(LocalDateTime.now().minusDays(1))
                .expiresAt(LocalDateTime.now().plusDays(20))
                .build();
        Product product = Product.builder()
                .id(7L)
                .code(CatalogProducts.QR_MENU)
                .name("Ek Menü")
                .build();
        UserEntitlement entitlement = UserEntitlement.builder()
                .id(1L)
                .userId(userId)
                .productId(7L)
                .productCode(CatalogProducts.QR_MENU)
                .purchaseId(20L)
                .totalQuantity(99)
                .remainingQuantity(99)
                .usedQuantity(0)
                .unlimited(false)
                .startsAt(addon.getStartsAt())
                .expiresAt(addon.getExpiresAt())
                .build();

        when(purchaseRepository.findByUserIdAndStatus(userId, PurchaseStatus.ACTIVE)).thenReturn(List.of(addon));
        when(productRepository.findByCode(CatalogProducts.QR_MENU)).thenReturn(Optional.of(product));
        when(entitlementRepository.findByPurchaseIdOrderByProductCodeAsc(20L)).thenReturn(List.of(entitlement));
        when(entitlementRepository.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of(entitlement));
        when(purchaseRepository.findAllById(List.of(20L))).thenReturn(List.of(addon));
        when(menuRepository.countActiveLiveMenusGroupedByBranch(userId)).thenReturn(List.of());
        when(qrRepository.countByUserIdAndDeletedFalse(userId)).thenReturn(0L);
        when(menuProductRepository.countActiveProductsForUser(userId)).thenReturn(0L);

        entitlementService.repairUsablePackageEntitlements(userId);

        verify(planPackageRepository, never()).findByIdWithItems(any());
        ArgumentCaptor<UserEntitlement> captor = ArgumentCaptor.forClass(UserEntitlement.class);
        verify(entitlementRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues().getFirst().getTotalQuantity()).isEqualTo(2);
        assertThat(captor.getAllValues().getFirst().getRemainingQuantity()).isEqualTo(2);
    }

    @Test
    void resolveActivePurchaseId_whenAddonAndPaidExist_thenReturnPaid() {
        Long userId = 9L;
        Purchase paid = Purchase.builder()
                .id(10L)
                .userId(userId)
                .packageId(4L)
                .packageCode("ULTIMATE_PACKAGE")
                .purchaseType(PurchaseType.PAID)
                .status(PurchaseStatus.ACTIVE)
                .startsAt(LocalDateTime.now().minusDays(1))
                .expiresAt(LocalDateTime.now().plusDays(20))
                .build();
        Purchase addon = Purchase.builder()
                .id(20L)
                .userId(userId)
                .packageId(4L)
                .packageCode(CatalogProducts.QR_BRANCH)
                .purchaseType(PurchaseType.ADD_ON)
                .status(PurchaseStatus.ACTIVE)
                .startsAt(LocalDateTime.now().minusDays(1))
                .expiresAt(LocalDateTime.now().plusDays(20))
                .build();
        when(purchaseRepository.findByUserIdAndStatusAndExpiresAtBefore(any(), any(), any()))
                .thenReturn(List.of());
        when(purchaseRepository.findByUserIdAndStatus(userId, PurchaseStatus.ACTIVE))
                .thenReturn(List.of(paid, addon));
        when(planPackageRepository.findAllById(List.of(4L))).thenReturn(List.of(
                com.ael.algoryqrservice.model.PlanPackage.builder().id(4L).priority(300).build()
        ));

        Long activeId = entitlementService.resolveActivePurchaseId(userId);

        assertThat(activeId).isEqualTo(10L);
    }
}
