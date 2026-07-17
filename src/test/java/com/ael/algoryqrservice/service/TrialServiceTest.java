package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogPackages;
import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.PlanPackage;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
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
    void startDigitalMenuPro_whenTrialHistoryExists_thenReject() {
        when(purchaseRepository.existsByUserIdAndPurchaseType(7L, PurchaseType.TRIAL)).thenReturn(true);

        assertThatThrownBy(() -> service.startDigitalMenuPro(7L))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void startDigitalMenuPro_whenAvailable_thenGrantExactlyThirtyDays() {
        PlanPackage plan = PlanPackage.builder()
                .id(2L)
                .code(CatalogPackages.PRO_PACKAGE)
                .name("PRO")
                .price(BigDecimal.TEN)
                .currency("TRY")
                .validityDays(30)
                .items(new ArrayList<>())
                .build();
        when(packageRepository.findFirstByTrialEligibleTrueAndActiveTrueOrderByPriorityDesc())
                .thenReturn(Optional.of(plan));
        when(purchaseRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            Purchase purchase = invocation.getArgument(0);
            purchase.setId(10L);
            return purchase;
        });

        TrialDtos.Status result = service.startDigitalMenuPro(7L);

        ArgumentCaptor<Purchase> captor = ArgumentCaptor.forClass(Purchase.class);
        verify(purchaseRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getPurchaseType()).isEqualTo(PurchaseType.TRIAL);
        assertThat(captor.getValue().getExpiresAt())
                .isEqualTo(captor.getValue().getStartsAt().plusDays(30));
        assertThat(result.lifecycle()).isEqualTo(TrialDtos.Lifecycle.ACTIVE);
        verify(packageActivationService).activatePurchasedPackage(any());
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
}
