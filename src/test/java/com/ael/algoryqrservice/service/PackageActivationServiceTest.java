package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogPackages;
import com.ael.algoryqrservice.catalog.CatalogProducts;
import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.PlanPackageItem;
import com.ael.algoryqrservice.model.Product;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.repository.PlanPackageRepository;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PackageActivationServiceTest {

    @Mock
    private PackageCatalogService packageCatalogService;
    @Mock
    private PlanPackageRepository planPackageRepository;
    @Mock
    private PurchaseRepository purchaseRepository;
    @Mock
    private EntitlementService entitlementService;
    @Mock
    private MenuPublicAccessService menuPublicAccessService;

    @InjectMocks
    private PackageActivationService packageActivationService;

    @Test
    void ensureFreePackage_whenUserHasNoActivePackage_thenCreateFreePackageAndGrantEntitlement() {
        Product product = Product.builder()
                .id(5L)
                .code(CatalogProducts.QR_CREATE)
                .name("QR")
                .active(true)
                .build();
        PlanPackage planPackage = PlanPackage.builder()
                .id(1L)
                .code(CatalogPackages.FREE_PACKAGE)
                .name("Free")
                .currency("TRY")
                .validityDays(30)
                .items(List.of(PlanPackageItem.builder()
                        .product(product)
                        .quantity(5)
                        .unlimited(false)
                        .build()))
                .build();

        when(purchaseRepository.findByUserIdAndStatus(20L, PurchaseStatus.ACTIVE)).thenReturn(List.of());
        when(packageCatalogService.ensureFreePackage()).thenReturn(planPackage);
        when(purchaseRepository.save(any(Purchase.class))).thenAnswer(invocation -> {
            Purchase purchase = invocation.getArgument(0);
            purchase.setId(100L);
            return purchase;
        });

        Purchase result = packageActivationService.ensureFreePackage(20L);

        assertThat(result.getPackageCode()).isEqualTo(CatalogPackages.FREE_PACKAGE);
        verify(entitlementService).grant(result, 5L, CatalogProducts.QR_CREATE, 5, false);
        verify(menuPublicAccessService).syncForUser(20L);
    }

    @Test
    void activatePurchasedPackage_whenFreePackageIsActive_thenSupersedeFreePackage() {
        Purchase freePurchase = Purchase.builder()
                .id(1L)
                .userId(20L)
                .packageCode(CatalogPackages.FREE_PACKAGE)
                .status(PurchaseStatus.ACTIVE)
                .build();
        Purchase proPurchase = Purchase.builder()
                .id(2L)
                .userId(20L)
                .packageCode(CatalogPackages.PRO_PACKAGE)
                .status(PurchaseStatus.ACTIVE)
                .build();
        when(purchaseRepository.findByUserIdAndStatus(20L, PurchaseStatus.ACTIVE))
                .thenReturn(List.of(freePurchase, proPurchase));

        packageActivationService.activatePurchasedPackage(proPurchase);

        ArgumentCaptor<Purchase> captor = ArgumentCaptor.forClass(Purchase.class);
        verify(purchaseRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(1L);
        assertThat(captor.getValue().getStatus()).isEqualTo(PurchaseStatus.SUPERSEDED);
    }
}
