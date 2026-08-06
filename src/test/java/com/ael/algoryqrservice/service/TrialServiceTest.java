package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogPackages;
import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.PlanPackageItem;
import com.ael.algoryqrservice.model.Product;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.dto.TrialDtos;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.repository.PlanPackageRepository;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.repository.UserRepository;
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
    UserRepository userRepository;
    @Mock
    EntitlementService entitlementService;
    @Mock
    PackageActivationService packageActivationService;
    @InjectMocks
    TrialService service;

    @Test
    void start_whenTrialHistoryExists_thenReject() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(User.builder().id(7L).trialUsed(false).build()));
        when(purchaseRepository.existsByUserIdAndPurchaseType(7L, PurchaseType.TRIAL)).thenReturn(true);

        assertThatThrownBy(() -> service.start(7L, 2L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("kullanilmis");
    }

    @Test
    void start_whenTrialUsedFlag_thenReject() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(User.builder().id(7L).trialUsed(true).build()));

        assertThatThrownBy(() -> service.start(7L, 2L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("kullanilmis");
        verify(purchaseRepository, never()).saveAndFlush(any());
    }

    @Test
    void start_whenPackageIdProvided_thenGrantForTrialDays() {
        PlanPackage plan = trialPackage(2L, CatalogPackages.PRO_PACKAGE, 30, 7);
        when(userRepository.findById(7L)).thenReturn(Optional.of(User.builder().id(7L).trialUsed(false).build()));
        when(purchaseRepository.existsByUserIdAndPurchaseType(7L, PurchaseType.TRIAL)).thenReturn(false);
        when(purchaseRepository.findByUserIdAndStatus(7L, PurchaseStatus.ACTIVE)).thenReturn(List.of());
        when(packageRepository.findByIdWithItems(2L)).thenReturn(Optional.of(plan));
        when(purchaseRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            Purchase purchase = invocation.getArgument(0);
            purchase.setId(10L);
            return purchase;
        });
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TrialDtos.Status result = service.start(7L, 2L);

        ArgumentCaptor<Purchase> captor = ArgumentCaptor.forClass(Purchase.class);
        verify(purchaseRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getPurchaseType()).isEqualTo(PurchaseType.TRIAL);
        assertThat(captor.getValue().getPackageId()).isEqualTo(2L);
        assertThat(captor.getValue().getExpiresAt())
                .isEqualTo(captor.getValue().getStartsAt().plusDays(7));
        assertThat(result.lifecycle()).isEqualTo(TrialDtos.Lifecycle.ACTIVE);
        verify(packageActivationService).activatePurchasedPackage(any());
        verify(entitlementService).grant(any(), any(), any(), any(Integer.class), any(Boolean.class));
    }

    @Test
    void start_whenIneligiblePackage_thenReject() {
        PlanPackage plan = trialPackage(2L, CatalogPackages.PRO_PACKAGE, 30, 7);
        plan.setTrialEligible(false);
        when(userRepository.findById(7L)).thenReturn(Optional.of(User.builder().id(7L).trialUsed(false).build()));
        when(purchaseRepository.existsByUserIdAndPurchaseType(7L, PurchaseType.TRIAL)).thenReturn(false);
        when(purchaseRepository.findByUserIdAndStatus(7L, PurchaseStatus.ACTIVE)).thenReturn(List.of());
        when(packageRepository.findByIdWithItems(2L)).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> service.start(7L, 2L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("uygun degil");
        verify(purchaseRepository, never()).saveAndFlush(any());
    }

    @Test
    void start_whenUltimateNotTrialEligible_thenReject() {
        PlanPackage plan = trialPackage(3L, CatalogPackages.ULTIMATE_PACKAGE, 30, null);
        plan.setTrialEligible(false);
        when(userRepository.findById(7L)).thenReturn(Optional.of(User.builder().id(7L).trialUsed(false).build()));
        when(purchaseRepository.existsByUserIdAndPurchaseType(7L, PurchaseType.TRIAL)).thenReturn(false);
        when(purchaseRepository.findByUserIdAndStatus(7L, PurchaseStatus.ACTIVE)).thenReturn(List.of());
        when(packageRepository.findByIdWithItems(3L)).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> service.start(7L, 3L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("uygun degil");
    }

    @Test
    void start_whenMissingTrialDays_thenReject() {
        PlanPackage plan = trialPackage(2L, CatalogPackages.PRO_PACKAGE, 30, null);
        when(userRepository.findById(7L)).thenReturn(Optional.of(User.builder().id(7L).trialUsed(false).build()));
        when(purchaseRepository.existsByUserIdAndPurchaseType(7L, PurchaseType.TRIAL)).thenReturn(false);
        when(purchaseRepository.findByUserIdAndStatus(7L, PurchaseStatus.ACTIVE)).thenReturn(List.of());
        when(packageRepository.findByIdWithItems(2L)).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> service.start(7L, 2L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("trialDays");
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
        when(userRepository.findById(7L)).thenReturn(Optional.of(User.builder().id(7L).trialUsed(false).build()));
        when(purchaseRepository.existsByUserIdAndPurchaseType(7L, PurchaseType.TRIAL)).thenReturn(false);
        when(purchaseRepository.findByUserIdAndStatus(7L, PurchaseStatus.ACTIVE)).thenReturn(List.of(paid));

        assertThatThrownBy(() -> service.start(7L, 2L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ucretli paket");
    }

    @Test
    void startDigitalMenuPro_whenAvailable_thenPinProPackage() {
        PlanPackage plan = trialPackage(2L, CatalogPackages.PRO_PACKAGE, 30, 7);
        when(packageRepository.findByCode(CatalogPackages.PRO_PACKAGE)).thenReturn(Optional.of(plan));
        when(packageRepository.findByIdWithItems(2L)).thenReturn(Optional.of(plan));
        when(userRepository.findById(7L)).thenReturn(Optional.of(User.builder().id(7L).trialUsed(false).build()));
        when(purchaseRepository.existsByUserIdAndPurchaseType(7L, PurchaseType.TRIAL)).thenReturn(false);
        when(purchaseRepository.findByUserIdAndStatus(7L, PurchaseStatus.ACTIVE)).thenReturn(List.of());
        when(purchaseRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            Purchase purchase = invocation.getArgument(0);
            purchase.setId(10L);
            return purchase;
        });
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TrialDtos.Status result = service.startDigitalMenuPro(7L);

        assertThat(result.lifecycle()).isEqualTo(TrialDtos.Lifecycle.ACTIVE);
        verify(packageRepository).findByCode(CatalogPackages.PRO_PACKAGE);
    }

    @Test
    void status_whenTrialDateExpired_thenExposeTrialExpiredAndRestoreFree() {
        User user = User.builder().id(7L).trialUsed(true).build();
        Purchase purchase = Purchase.builder().id(10L).userId(7L).purchaseType(PurchaseType.TRIAL)
                .status(PurchaseStatus.ACTIVE).startsAt(LocalDateTime.now().minusDays(31))
                .expiresAt(LocalDateTime.now().minusDays(1)).build();
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
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

    @Test
    void status_whenTrialExistsButTrialUsedFalse_thenBackfillTrue() {
        User user = User.builder().id(7L).trialUsed(false).build();
        Purchase purchase = Purchase.builder().id(10L).userId(7L).purchaseType(PurchaseType.TRIAL)
                .status(PurchaseStatus.ACTIVE).startsAt(LocalDateTime.now().minusDays(1))
                .expiresAt(LocalDateTime.now().plusDays(5)).build();
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(purchaseRepository.findFirstByUserIdAndPurchaseTypeOrderByPurchasedAtDesc(7L, PurchaseType.TRIAL))
                .thenReturn(Optional.of(purchase));
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(packageRepository.findById(any())).thenReturn(Optional.empty());

        TrialDtos.Status result = service.status(7L);

        assertThat(result.lifecycle()).isEqualTo(TrialDtos.Lifecycle.ACTIVE);
        assertThat(user.isTrialUsed()).isTrue();
        verify(userRepository).save(user);
    }

    private PlanPackage trialPackage(Long id, String code, int validityDays, Integer trialDays) {
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
                .trialDays(trialDays)
                .active(true)
                .trialEligible(true)
                .systemManaged(false)
                .items(new ArrayList<>(List.of(item)))
                .build();
    }
}
