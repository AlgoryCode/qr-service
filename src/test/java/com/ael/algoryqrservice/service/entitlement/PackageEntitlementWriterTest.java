package com.ael.algoryqrservice.service.entitlement;

import com.ael.algoryqrservice.catalog.CatalogProducts;
import com.ael.algoryqrservice.model.Product;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.UserEntitlement;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.repository.ProductRepository;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.repository.UserEntitlementRepository;
import com.ael.algoryqrservice.service.PurchaseLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PackageEntitlementWriterTest {

    private static final Long USER_ID = 9L;

    @Mock
    private UserEntitlementRepository entitlementRepository;
    @Mock
    private PurchaseRepository purchaseRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private PurchaseLogService purchaseLogService;

    @InjectMocks
    private PackageEntitlementWriter entitlementWriter;

    @Captor
    private ArgumentCaptor<UserEntitlement> entitlementCaptor;

    @Test
    void repairAddonEntitlements_whenInstallmentCountIsTwo_thenResizeQuantityToTwo() {
        Purchase addon = addonPurchase(2);
        Product product = Product.builder().id(7L).code(CatalogProducts.QR_MENU).name("Ek Menü").build();
        UserEntitlement entitlement = UserEntitlement.builder()
                .id(1L)
                .userId(USER_ID)
                .productId(7L)
                .productCode(CatalogProducts.QR_MENU)
                .purchaseId(addon.getId())
                .totalQuantity(99)
                .remainingQuantity(99)
                .usedQuantity(0)
                .unlimited(false)
                .build();

        when(productRepository.findByCode(CatalogProducts.QR_MENU)).thenReturn(Optional.of(product));
        when(entitlementRepository.findByPurchaseIdOrderByProductCodeAsc(addon.getId()))
                .thenReturn(List.of(entitlement));

        entitlementWriter.repairAddonEntitlements(addon);

        verify(entitlementRepository, atLeastOnce()).save(entitlementCaptor.capture());
        UserEntitlement saved = entitlementCaptor.getValue();
        assertThat(saved.getTotalQuantity()).isEqualTo(2);
        assertThat(saved.getRemainingQuantity()).isEqualTo(2);
    }

    @Test
    void repairAddonEntitlements_whenUsageExceedsNewQuantity_thenClampUsageAndZeroRemaining() {
        Purchase addon = addonPurchase(1);
        Product product = Product.builder().id(7L).code(CatalogProducts.QR_MENU).name("Ek Menü").build();
        UserEntitlement entitlement = UserEntitlement.builder()
                .id(1L)
                .userId(USER_ID)
                .productId(7L)
                .productCode(CatalogProducts.QR_MENU)
                .purchaseId(addon.getId())
                .totalQuantity(5)
                .remainingQuantity(2)
                .usedQuantity(3)
                .unlimited(false)
                .build();

        when(productRepository.findByCode(CatalogProducts.QR_MENU)).thenReturn(Optional.of(product));
        when(entitlementRepository.findByPurchaseIdOrderByProductCodeAsc(addon.getId()))
                .thenReturn(List.of(entitlement));

        entitlementWriter.repairAddonEntitlements(addon);

        assertThat(entitlement.getUsedQuantity()).isEqualTo(1);
        assertThat(entitlement.getTotalQuantity()).isEqualTo(1);
        assertThat(entitlement.getRemainingQuantity()).isZero();
    }

    @Test
    void repairAddonEntitlements_whenStartDateIsInFuture_thenPullItForward() {
        Purchase addon = addonPurchase(1);
        addon.setStartsAt(LocalDateTime.now().plusDays(3));
        when(productRepository.findByCode(CatalogProducts.QR_MENU)).thenReturn(Optional.empty());

        entitlementWriter.repairAddonEntitlements(addon);

        assertThat(addon.getStartsAt()).isBeforeOrEqualTo(LocalDateTime.now());
        verify(purchaseRepository).save(addon);
    }

    private Purchase addonPurchase(int installmentCount) {
        return Purchase.builder()
                .id(20L)
                .userId(USER_ID)
                .packageId(4L)
                .packageCode(CatalogProducts.QR_MENU)
                .purchaseType(PurchaseType.ADD_ON)
                .installmentCount(installmentCount)
                .status(PurchaseStatus.ACTIVE)
                .startsAt(LocalDateTime.now().minusDays(1))
                .expiresAt(LocalDateTime.now().plusDays(20))
                .build();
    }
}
