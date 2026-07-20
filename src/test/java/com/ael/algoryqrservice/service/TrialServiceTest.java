package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogPackages;
import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.Product;
import com.ael.algoryqrservice.model.PlanPackageItem;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.dto.TrialDtos;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrialServiceTest {

    @Mock
    PurchaseRepository purchaseRepository;
    @Mock
    PlanPackageRepository packageRepository;
    @Mock
    EntitlementService entitlementService;
    @Mock
    PackageActivationService packageActivationService;
    @InjectMocks
    TrialService service;

    @Test
    void start_whenTrialHistoryExists_thenReject() {
        when(purchaseRepository.existsByUserIdAndPurchaseType(7L, PurchaseType.TRIAL)).thenReturn(true);

        assertThatThrownBy(() -> service.start(7L, 2L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("kullanilmis");
    }

    @Test
    void start_whenPackageIdProvided_thenGrantForValidityDays() {
        PlanPackage plan = trialPackage(2L, CatalogPackages.PRO_PACKAGE, 30);
        when(purchaseRepository.existsByUserIdAndPurchaseType(7L, PurchaseType.TRIAL)).thenReturn(false);
        when(purchaseRepository.findByUserIdAndStatus(7L, PurchaseStatus.ACTIVE)).thenReturn(List.of());
        when(packageRepository.findByIdWithItems(2L)).thenReturn(Optional.of(plan));
        when(purchaseRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            Purchase purchase = invocation.getArgument(0);
            purchase.setId(10L);
            return purchase;
        });

        TrialDtos.Status result = service.start(7L, 2L);

        ArgumentCaptor<Purchase> captor = ArgumentCaptor.forClass(Purchase.class);
        verify(purchaseRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getPurchaseType()).isEqualTo(PurchaseType.TRIAL);
        assertThat(captor.getValue().getPackageId()).isEqualTo(2L);
        assertThat(captor.getValue().getExpiresAt())
                .isEqualTo(captor.getValue().getStartsAt().plusDays(30));
        assertThat(result.lifecycle()).isEqualTo(TrialDtos.Lifecycle.ACTIVE);
        verify(packageActivationService).activatePurchasedPackage(any());
        verify(entitlementService).grant(any(), any(), any(), any(Integer.class), any(Boolean.class));
    }

    @Test
    void start_whenIneligiblePackage_thenReject() {
        PlanPackage plan = trialPackage(2L, CatalogPackages.PRO_PACKAGE, 30);
        plan.setTrialEligible(false);
        when(purchaseRepository.existsByUserIdAndPurchaseType(7L, PurchaseType.TRIAL)).thenReturn(false);
        when(purchaseRepository.findByUserIdAndStatus(7L, PurchaseStatus.ACTIVE)).thenReturn(List.of());
        when(packageRepository.findByIdWithItems(2L)).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> service.start(7L, 2L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("uygun degil");
        verify(purchaseRepository, never()).saveAndFlush(any());
    }

    @Test
    void start_whenUsablePaidExists_thenReject() {
        Purchase paid = Purchase.builder()
                .id(1L)
                .userId(7L)
                .purchaseType(PurchaseType.PAID)
                .status(PurchaseStatus.ACTIVE)
                .startsAt(LocalDateTime.now().minusDays(1))
                .expiresAt(LocalDateTime.now().plusDays(10))
                .build();
        when(purchaseRepository.existsByUserIdAndPurchaseType(7L, PurchaseType.TRIAL)).thenReturn(false);
        when(purchaseRepository.findByUserIdAndStatus(7L, PurchaseStatus.ACTIVE)).thenReturn(List.of(paid));

        assertThatThrownBy(() -> service.start(7L, 2L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ucretli paket");
    }

    @Test
    void startDigitalMenuPro_whenAvailable_thenDelegateToHighestPriority() {
        PlanPackage plan = trialPackage(2L, CatalogPackages.PRO_PACKAGE, 30);
        when(packageRepository.findFirstByTrialEligibleTrueAndActiveTrueOrderByPriorityDesc())
                .thenReturn(Optional.of(plan));
        when(purchaseRepository.existsByUserIdAndPurchaseType(7L, PurchaseType.TRIAL)).thenReturn(false);
        when(purchaseRepository.findByUserIdAndStatus(7L, PurchaseStatus.ACTIVE)).thenReturn(List.of());
        when(packageRepository.findByIdWithItems(2L)).thenReturn(Optional.of(plan));
        when(purchaseRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            Purchase purchase = invocation.getArgument(0);
            purchase.setId(10L);
            return purchase;
        });

        TrialDtos.Status result = service.startDigitalMenuPro(7L);

        assertThat(result.lifecycle()).isEqualTo(TrialDtos.Lifecycle.ACTIVE);
    }

    @Test
    void status_whenTrialDateExpired_thenExposeTrialExpiredAndRestoreFree() {
        Purchase purchase = Purchase.builder().id(10L).userId(7L).purchaseType(PurchaseType.TRIAL)
                .status(PurchaseStatus.ACTIVE).startsAt(LocalDateTime.now().minusDays(31))
                .expiresAt(LocalDateTime.now().minusDays(1)).build();
        when(purchaseRepository.findFirstByUserIdAndPurchaseTypeOrderByPurchasedAtDesc(7L, PurchaseType.TRIAL))
                .thenReturn(Optional.of(purchase));
        doAnswer(invocation -> {
            purchase.setStatus(PurchaseStatus.EXPIRED);
            return null;
        }).when(entitlementService).expirePurchase(purchase);

        TrialDtos.Status result = service.status(7L);

        assertThat(result.lifecycle()).isEqualTo(TrialDtos.Lifecycle.TRIAL_EXPIRED);
        verify(packageActivationService).ensureFreePackage(7L);
    }

    private PlanPackage trialPackage(Long id, String code, int validityDays) {
        Product product = Product.builder().id(7L).code("QR_CREATE").name("QR").build();
        PlanPackageItem item = PlanPackageItem.builder()
                .id(1L)
                .product(product)
                .quantity(30)
                .unlimited(false)
                .build();
        return PlanPackage.builder()
                .id(id)
                .code(code)
                .name("PRO")
                .price(BigDecimal.TEN)
                .currency("TRY")
                .validityDays(validityDays)
                .active(true)
                .trialEligible(true)
                .systemManaged(false)
                .items(new ArrayList<>(List.of(item)))
                .build();
    }
}
