package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogPackages;
import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.repository.PlanPackageRepository;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.util.AppTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PackageActivationServiceTest {

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

    @BeforeEach
    void freezeClock() {
        AppTime.setClock(Clock.fixed(
                LocalDateTime.of(2026, 8, 15, 12, 0, 0).atZone(AppTime.ZONE).toInstant(),
                AppTime.ZONE
        ));
    }

    @AfterEach
    void resetClock() {
        AppTime.resetClock();
    }

    @Test
    void ensureSubscriptionState_whenActiveTrialExists_thenReturnTrial() {
        Purchase trial = Purchase.builder()
                .id(102L)
                .userId(20L)
                .packageId(4L)
                .packageCode(CatalogPackages.ULTIMATE_PACKAGE)
                .purchaseType(PurchaseType.TRIAL)
                .status(PurchaseStatus.ACTIVE)
                .startsAt(LocalDateTime.of(2026, 8, 15, 11, 0, 0))
                .expiresAt(LocalDateTime.of(2026, 9, 14, 11, 0, 0))
                .build();
        PlanPackage ultimate = PlanPackage.builder()
                .id(4L)
                .code(CatalogPackages.ULTIMATE_PACKAGE)
                .priority(300)
                .build();

        when(purchaseRepository.findByUserIdAndStatus(20L, PurchaseStatus.ACTIVE)).thenReturn(List.of(trial));
        when(planPackageRepository.findAllById(List.of(4L))).thenReturn(List.of(ultimate));

        Optional<Purchase> result = packageActivationService.ensureSubscriptionState(20L);

        assertThat(result).contains(trial);
        verify(menuPublicAccessService).syncForUser(20L);
    }

    @Test
    void ensureSubscriptionState_whenNoActivePaidOrTrial_thenReturnEmpty() {
        when(purchaseRepository.findByUserIdAndStatus(20L, PurchaseStatus.ACTIVE)).thenReturn(List.of());

        Optional<Purchase> result = packageActivationService.ensureSubscriptionState(20L);

        assertThat(result).isEmpty();
        verify(menuPublicAccessService).syncForUser(20L);
    }

    @Test
    void activatePurchasedPackage_whenAnotherActiveExists_thenSupersedePrevious() {
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

        packageActivationService.activatePurchasedPackage(proPurchase);

        ArgumentCaptor<Purchase> captor = ArgumentCaptor.forClass(Purchase.class);
        verify(purchaseRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(1L);
        assertThat(captor.getValue().getStatus()).isEqualTo(PurchaseStatus.SUPERSEDED);
        verify(menuPublicAccessService).syncForUser(20L);
    }

    @Test
    void activatePurchasedPackage_whenAddon_thenKeepExistingActive() {
        Purchase host = Purchase.builder()
                .id(1L)
                .userId(20L)
                .packageCode(CatalogPackages.ULTIMATE_PACKAGE)
                .purchaseType(PurchaseType.SYSTEM_GRANT)
                .status(PurchaseStatus.ACTIVE)
                .build();
        Purchase addon = Purchase.builder()
                .id(2L)
                .userId(20L)
                .packageCode("QR_MENU")
                .purchaseType(PurchaseType.ADD_ON)
                .status(PurchaseStatus.ACTIVE)
                .build();
        when(purchaseRepository.findByUserIdAndStatus(20L, PurchaseStatus.ACTIVE))
                .thenReturn(List.of(host, addon));

        packageActivationService.activatePurchasedPackage(addon);

        verify(purchaseRepository, never()).save(any());
        verify(menuPublicAccessService).syncForUser(20L);
    }

    @Test
    void ensureSubscriptionState_whenAddonAndPaidExist_thenReturnPaid() {
        Purchase paid = Purchase.builder()
                .id(1L)
                .userId(20L)
                .packageId(4L)
                .packageCode(CatalogPackages.ULTIMATE_PACKAGE)
                .purchaseType(PurchaseType.PAID)
                .status(PurchaseStatus.ACTIVE)
                .startsAt(LocalDateTime.of(2026, 8, 15, 11, 0, 0))
                .expiresAt(LocalDateTime.of(2026, 9, 14, 11, 0, 0))
                .build();
        Purchase addon = Purchase.builder()
                .id(2L)
                .userId(20L)
                .packageId(4L)
                .packageCode("QR_MENU")
                .purchaseType(PurchaseType.ADD_ON)
                .status(PurchaseStatus.ACTIVE)
                .startsAt(LocalDateTime.of(2026, 8, 15, 11, 0, 0))
                .expiresAt(LocalDateTime.of(2026, 9, 14, 11, 0, 0))
                .build();
        PlanPackage ultimate = PlanPackage.builder()
                .id(4L)
                .code(CatalogPackages.ULTIMATE_PACKAGE)
                .priority(300)
                .build();

        when(purchaseRepository.findByUserIdAndStatus(20L, PurchaseStatus.ACTIVE))
                .thenReturn(List.of(paid, addon));
        when(planPackageRepository.findAllById(List.of(4L))).thenReturn(List.of(ultimate));

        Optional<Purchase> result = packageActivationService.ensureSubscriptionState(20L);

        assertThat(result).contains(paid);
    }

    @Test
    void ensureSubscriptionState_whenOnlyAddonExists_thenReturnEmpty() {
        Purchase addon = Purchase.builder()
                .id(2L)
                .userId(20L)
                .packageId(4L)
                .packageCode("QR_MENU")
                .purchaseType(PurchaseType.ADD_ON)
                .status(PurchaseStatus.ACTIVE)
                .startsAt(LocalDateTime.of(2026, 8, 15, 11, 0, 0))
                .expiresAt(LocalDateTime.of(2026, 9, 14, 11, 0, 0))
                .build();

        when(purchaseRepository.findByUserIdAndStatus(20L, PurchaseStatus.ACTIVE))
                .thenReturn(List.of(addon));

        Optional<Purchase> result = packageActivationService.ensureSubscriptionState(20L);

        assertThat(result).isEmpty();
    }
}
