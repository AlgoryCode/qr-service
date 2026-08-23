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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDateTime;
import java.util.List;

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

    @InjectMocks
    private EntitlementService entitlementService;

    @BeforeEach
    void stubMenuCounts() {
        org.mockito.Mockito.lenient().when(menuRepository.countActiveLiveMenusGroupedByBranch(any()))
                .thenReturn(List.of());
    }

    @Test
    void assertMenuProductCreationAllowed_whenOnlyUnlimitedEntitlement_thenAllowsCreation() {
        Long userId = 13L;
        UserEntitlement entitlement = UserEntitlement.builder()
                .id(191L)
                .userId(userId)
                .productCode(CatalogProducts.MENU_PRODUCT)
                .purchaseId(104L)
                .totalQuantity(1)
                .remainingQuantity(1)
                .usedQuantity(0)
                .unlimited(true)
                .build();
        Purchase purchase = Purchase.builder()
                .id(104L)
                .userId(userId)
                .status(PurchaseStatus.ACTIVE)
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();

        when(purchaseRepository.findByUserIdAndStatus(userId, PurchaseStatus.ACTIVE)).thenReturn(List.of(purchase));
        when(entitlementRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(entitlement));
        when(entitlementRepository.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of(entitlement));
        when(purchaseRepository.findAllById(any())).thenReturn(List.of(purchase));
        when(menuProductRepository.countActiveProductsForUser(userId)).thenReturn(0L);

        assertThatCode(() -> entitlementService.assertMenuProductCreationAllowed(userId, 1))
                .doesNotThrowAnyException();
    }

    @Test
    void assertMenuActivationAllowed_whenOnlyUnlimitedEntitlement_thenAllowsActivation() {
        Long userId = 13L;
        UserEntitlement entitlement = UserEntitlement.builder()
                .id(193L)
                .userId(userId)
                .productCode(CatalogProducts.QR_MENU)
                .purchaseId(104L)
                .totalQuantity(1)
                .remainingQuantity(1)
                .usedQuantity(0)
                .unlimited(true)
                .build();
        Purchase purchase = Purchase.builder()
                .id(104L)
                .userId(userId)
                .status(PurchaseStatus.ACTIVE)
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();

        when(purchaseRepository.findByUserIdAndStatus(userId, PurchaseStatus.ACTIVE)).thenReturn(List.of(purchase));
        when(entitlementRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(entitlement));
        when(entitlementRepository.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of(entitlement));
        when(purchaseRepository.findAllById(any())).thenReturn(List.of(purchase));

        assertThatCode(() -> entitlementService.assertMenuActivationAllowed(userId))
                .doesNotThrowAnyException();
    }
}
