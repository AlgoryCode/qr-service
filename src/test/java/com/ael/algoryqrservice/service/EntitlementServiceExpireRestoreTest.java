package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.repository.PlanPackageRepository;
import com.ael.algoryqrservice.repository.ProductRepository;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.repository.QrRepository;
import com.ael.algoryqrservice.repository.UserEntitlementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntitlementServiceExpireRestoreTest {

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
    private QrRepository qrRepository;
    @Mock
    private ObjectProvider<PackageActivationService> packageActivationServiceProvider;
    @Mock
    private PackageActivationService packageActivationService;

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
                qrRepository,
                packageActivationServiceProvider
        );
        when(packageActivationServiceProvider.getObject()).thenReturn(packageActivationService);
    }

    @Test
    void expirePurchase_whenActiveTrial_thenRestoreFreeThenSync() {
        Purchase purchase = Purchase.builder()
                .id(10L)
                .userId(7L)
                .packageName("Pro")
                .purchaseType(PurchaseType.TRIAL)
                .status(PurchaseStatus.ACTIVE)
                .startsAt(LocalDateTime.now().minusDays(8))
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .build();

        entitlementService.expirePurchase(purchase);

        assertThat(purchase.getStatus()).isEqualTo(PurchaseStatus.EXPIRED);
        verify(packageActivationService).ensureFreePackage(7L);
        verify(menuPublicAccessService).syncForUser(7L);
    }

    @Test
    void expireDuePurchasesForUser_whenDueExists_thenRestoreFreeOnce() {
        Purchase purchase = Purchase.builder()
                .id(10L)
                .userId(7L)
                .packageName("Pro")
                .purchaseType(PurchaseType.TRIAL)
                .status(PurchaseStatus.ACTIVE)
                .startsAt(LocalDateTime.now().minusDays(8))
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .build();
        when(purchaseRepository.findByUserIdAndStatusAndExpiresAtBefore(
                org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq(PurchaseStatus.ACTIVE),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class)
        )).thenReturn(List.of(purchase));

        entitlementService.expireDuePurchasesForUser(7L);

        assertThat(purchase.getStatus()).isEqualTo(PurchaseStatus.EXPIRED);
        verify(packageActivationService).ensureFreePackage(7L);
        verify(menuPublicAccessService).syncForUser(7L);
    }
}
