package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogProducts;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.UserEntitlement;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.repository.ProductRepository;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.repository.UserEntitlementRepository;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntitlementServiceHasUsableQrMenuPackageTest {

    @Mock
    private UserEntitlementRepository entitlementRepository;
    @Mock
    private PurchaseRepository purchaseRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private PurchaseLogService purchaseLogService;
    @Mock
    private MenuPublicAccessService menuPublicAccessService;

    @InjectMocks
    private EntitlementService entitlementService;

    @Test
    void hasUsableQrMenuPackage_whenRemainingZeroButPurchaseActive_thenTrue() {
        Long userId = 7L;
        UserEntitlement entitlement = UserEntitlement.builder()
                .id(1L)
                .userId(userId)
                .productCode(CatalogProducts.QR_MENU)
                .purchaseId(10L)
                .totalQuantity(1)
                .remainingQuantity(0)
                .usedQuantity(1)
                .unlimited(false)
                .build();
        Purchase purchase = Purchase.builder()
                .id(10L)
                .userId(userId)
                .status(PurchaseStatus.ACTIVE)
                .expiresAt(LocalDateTime.now().plusDays(10))
                .build();

        when(purchaseRepository.findByStatusAndExpiresAtBefore(eq(PurchaseStatus.ACTIVE), any()))
                .thenReturn(List.of());
        when(entitlementRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(entitlement));
        when(purchaseRepository.findAllById(List.of(10L))).thenReturn(List.of(purchase));

        assertThat(entitlementService.hasUsableQrMenuPackage(userId)).isTrue();
    }

    @Test
    void hasUsableQrMenuPackage_whenPurchaseExpiredByDate_thenFalse() {
        Long userId = 7L;
        UserEntitlement entitlement = UserEntitlement.builder()
                .id(1L)
                .userId(userId)
                .productCode(CatalogProducts.QR_MENU)
                .purchaseId(10L)
                .totalQuantity(1)
                .remainingQuantity(1)
                .usedQuantity(0)
                .unlimited(false)
                .build();
        Purchase purchase = Purchase.builder()
                .id(10L)
                .userId(userId)
                .status(PurchaseStatus.ACTIVE)
                .expiresAt(LocalDateTime.now().minusDays(1))
                .build();

        when(purchaseRepository.findByStatusAndExpiresAtBefore(eq(PurchaseStatus.ACTIVE), any()))
                .thenReturn(List.of());
        when(entitlementRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(entitlement));
        when(purchaseRepository.findAllById(List.of(10L))).thenReturn(List.of(purchase));

        assertThat(entitlementService.hasUsableQrMenuPackage(userId)).isFalse();
    }
}
