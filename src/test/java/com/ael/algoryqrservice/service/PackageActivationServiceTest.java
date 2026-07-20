package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogPackages;
import com.ael.algoryqrservice.catalog.CatalogProducts;
import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.PlanPackageItem;
import com.ael.algoryqrservice.model.Product;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.repository.PlanPackageRepository;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
    void ensureFreePackage_whenUserHasNoPurchase_thenCreateActiveFree() {
        PlanPackage freePackage = freePlan();
        when(purchaseRepository.findByUserIdAndStatus(20L, PurchaseStatus.ACTIVE)).thenReturn(List.of());
        when(purchaseRepository.findFirstByUserIdAndPurchaseTypeOrderByPurchasedAtDesc(20L, PurchaseType.FREE))
                .thenReturn(Optional.empty());
        when(packageCatalogService.ensureFreePackage()).thenReturn(freePackage);
        when(purchaseRepository.save(any(Purchase.class))).thenAnswer(invocation -> {
            Purchase purchase = invocation.getArgument(0);
            purchase.setId(100L);
            return purchase;
        });

        Purchase result = packageActivationService.ensureFreePackage(20L);

        assertThat(result.getPackageCode()).isEqualTo(CatalogPackages.FREE_PACKAGE);
        assertThat(result.getStatus()).isEqualTo(PurchaseStatus.ACTIVE);
        verify(entitlementService).refreshForPackage(result, freePackage);
        verify(menuPublicAccessService).syncForUser(20L);
    }

    @Test
    void ensureFreePackage_whenProActive_thenReturnProAndKeepFreeSuperseded() {
        Purchase free = Purchase.builder()
                .id(1L)
                .userId(20L)
                .packageId(1L)
                .packageCode(CatalogPackages.FREE_PACKAGE)
                .purchaseType(PurchaseType.FREE)
                .status(PurchaseStatus.SUPERSEDED)
                .startsAt(LocalDateTime.now().minusDays(1))
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();
        Purchase pro = Purchase.builder()
                .id(2L)
                .userId(20L)
                .packageId(2L)
                .packageCode(CatalogPackages.PRO_PACKAGE)
                .purchaseType(PurchaseType.PAID)
                .status(PurchaseStatus.ACTIVE)
                .startsAt(LocalDateTime.now().minusDays(1))
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();
        PlanPackage proPackage = PlanPackage.builder().id(2L).code(CatalogPackages.PRO_PACKAGE).priority(100).build();

        when(purchaseRepository.findByUserIdAndStatus(20L, PurchaseStatus.ACTIVE)).thenReturn(List.of(pro));
        when(purchaseRepository.findFirstByUserIdAndPurchaseTypeOrderByPurchasedAtDesc(20L, PurchaseType.FREE))
                .thenReturn(Optional.of(free));
        when(planPackageRepository.findAllById(List.of(2L))).thenReturn(List.of(proPackage));

        Purchase result = packageActivationService.ensureFreePackage(20L);

        assertThat(result.getId()).isEqualTo(2L);
        assertThat(result.getPackageCode()).isEqualTo(CatalogPackages.PRO_PACKAGE);
        verify(entitlementService, never()).refreshForPackage(any(), any());
        verify(menuPublicAccessService, never()).syncForUser(20L);
    }

    @Test
    void ensureFreePackage_whenProExpired_thenReactivateExistingFree() {
        Purchase free = Purchase.builder()
                .id(1L)
                .userId(20L)
                .packageId(1L)
                .packageCode(CatalogPackages.FREE_PACKAGE)
                .purchaseType(PurchaseType.FREE)
                .status(PurchaseStatus.SUPERSEDED)
                .startsAt(LocalDateTime.now().minusDays(40))
                .expiresAt(LocalDateTime.now().minusDays(10))
                .build();
        PlanPackage freePackage = freePlan();

        when(purchaseRepository.findByUserIdAndStatus(20L, PurchaseStatus.ACTIVE)).thenReturn(List.of());
        when(purchaseRepository.findFirstByUserIdAndPurchaseTypeOrderByPurchasedAtDesc(20L, PurchaseType.FREE))
                .thenReturn(Optional.of(free));
        when(packageCatalogService.ensureFreePackage()).thenReturn(freePackage);
        when(purchaseRepository.save(any(Purchase.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Purchase result = packageActivationService.ensureFreePackage(20L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getStatus()).isEqualTo(PurchaseStatus.ACTIVE);
        verify(entitlementService).refreshForPackage(result, freePackage);
        verify(menuPublicAccessService).syncForUser(20L);
    }

    @Test
    void activatePurchasedPackage_whenFreeActive_thenSupersedeFreeAndKeepBaseline() {
        Purchase freePurchase = Purchase.builder()
                .id(1L)
                .userId(20L)
                .packageCode(CatalogPackages.FREE_PACKAGE)
                .purchaseType(PurchaseType.FREE)
                .status(PurchaseStatus.ACTIVE)
                .build();
        Purchase proPurchase = Purchase.builder()
                .id(2L)
                .userId(20L)
                .packageCode(CatalogPackages.PRO_PACKAGE)
                .purchaseType(PurchaseType.PAID)
                .status(PurchaseStatus.ACTIVE)
                .build();
        when(purchaseRepository.findByUserIdAndStatus(20L, PurchaseStatus.ACTIVE))
                .thenReturn(List.of(freePurchase, proPurchase));
        when(purchaseRepository.findFirstByUserIdAndPurchaseTypeOrderByPurchasedAtDesc(20L, PurchaseType.FREE))
                .thenReturn(Optional.of(freePurchase));

        packageActivationService.activatePurchasedPackage(proPurchase);

        ArgumentCaptor<Purchase> captor = ArgumentCaptor.forClass(Purchase.class);
        verify(purchaseRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(1L);
        assertThat(captor.getValue().getStatus()).isEqualTo(PurchaseStatus.SUPERSEDED);
        verify(menuPublicAccessService).syncForUser(20L);
    }

    private PlanPackage freePlan() {
        Product product = Product.builder()
                .id(5L)
                .code(CatalogProducts.QR_CREATE)
                .name("QR")
                .active(true)
                .build();
        return PlanPackage.builder()
                .id(1L)
                .code(CatalogPackages.FREE_PACKAGE)
                .name("Free")
                .currency("TRY")
                .validityDays(36500)
                .priority(1)
                .items(List.of(PlanPackageItem.builder()
                        .product(product)
                        .quantity(5)
                        .unlimited(false)
                        .build()))
                .build();
    }
}
