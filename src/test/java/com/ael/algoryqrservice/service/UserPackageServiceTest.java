package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.PlanPackageItem;
import com.ael.algoryqrservice.model.Product;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.enums.PackageCode;
import com.ael.algoryqrservice.model.enums.ProductCode;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserPackageServiceTest {

    @Mock
    private PackageCatalogService packageCatalogService;
    @Mock
    private PurchaseRepository purchaseRepository;
    @Mock
    private EntitlementService entitlementService;
    @InjectMocks
    private UserPackageService userPackageService;

    @Test
    void ensureFreePackage_whenUserHasNoActivePackage_thenCreateFreePackageAndGrantEntitlement() {
        Product product = Product.builder()
                .id(5L)
                .code(ProductCode.QR_CREATE)
                .build();
        PlanPackage planPackage = PlanPackage.builder()
                .id(10L)
                .code(PackageCode.FREE_PACKAGE)
                .name("Free")
                .price(BigDecimal.ZERO)
                .currency("TRY")
                .validityDays(36500)
                .items(List.of(PlanPackageItem.builder()
                        .product(product)
                        .quantity(5)
                        .unlimited(false)
                        .build()))
                .build();
        when(purchaseRepository.findByUserIdAndStatus(20L, PurchaseStatus.ACTIVE))
                .thenReturn(List.of());
        when(packageCatalogService.ensureFreePackage()).thenReturn(planPackage);
        when(purchaseRepository.save(any(Purchase.class))).thenAnswer(invocation -> {
            Purchase purchase = invocation.getArgument(0);
            purchase.setId(30L);
            return purchase;
        });

        Purchase result = userPackageService.ensureFreePackage(20L);

        assertThat(result.getPackageCode()).isEqualTo(PackageCode.FREE_PACKAGE);
        assertThat(result.getStatus()).isEqualTo(PurchaseStatus.ACTIVE);
        verify(entitlementService).grant(result, 5L, ProductCode.QR_CREATE, 5, false);
    }

    @Test
    void activateProPackage_whenFreePackageIsActive_thenSupersedeFreePackage() {
        Purchase freePurchase = Purchase.builder()
                .id(1L)
                .userId(20L)
                .packageCode(PackageCode.FREE_PACKAGE)
                .status(PurchaseStatus.ACTIVE)
                .build();
        Purchase proPurchase = Purchase.builder()
                .id(2L)
                .userId(20L)
                .packageCode(PackageCode.PRO_PACKAGE)
                .status(PurchaseStatus.ACTIVE)
                .build();
        when(purchaseRepository.findByUserIdAndStatus(20L, PurchaseStatus.ACTIVE))
                .thenReturn(List.of(freePurchase, proPurchase));

        userPackageService.activateProPackage(proPurchase);

        ArgumentCaptor<Purchase> captor = ArgumentCaptor.forClass(Purchase.class);
        verify(purchaseRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(1L);
        assertThat(captor.getValue().getStatus()).isEqualTo(PurchaseStatus.SUPERSEDED);
        assertThat(proPurchase.getStatus()).isEqualTo(PurchaseStatus.ACTIVE);
    }
}
