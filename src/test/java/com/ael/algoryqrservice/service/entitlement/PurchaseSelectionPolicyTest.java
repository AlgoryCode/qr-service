package com.ael.algoryqrservice.service.entitlement;

import com.ael.algoryqrservice.catalog.CatalogProducts;
import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.repository.PlanPackageRepository;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.util.AppTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseSelectionPolicyTest {

    private static final Long USER_ID = 9L;

    @Mock
    private PurchaseRepository purchaseRepository;
    @Mock
    private PlanPackageRepository planPackageRepository;

    @InjectMocks
    private PurchaseSelectionPolicy purchaseSelectionPolicy;

    @AfterEach
    void resetClock() {
        AppTime.resetClock();
    }

    @Test
    void activePurchaseId_whenAddonAndPaidExist_thenReturnPaid() {
        Purchase paid = purchase(10L, PurchaseType.PAID, "ULTIMATE_PACKAGE");
        Purchase addon = purchase(20L, PurchaseType.ADD_ON, CatalogProducts.QR_BRANCH);
        when(purchaseRepository.findByUserIdAndStatus(USER_ID, PurchaseStatus.ACTIVE))
                .thenReturn(List.of(paid, addon));
        when(planPackageRepository.findAllById(List.of(4L)))
                .thenReturn(List.of(PlanPackage.builder().id(4L).priority(300).build()));

        assertThat(purchaseSelectionPolicy.activePurchaseId(USER_ID)).isEqualTo(10L);
    }

    @Test
    void activePurchaseId_whenOnlyAddonsExist_thenReturnNull() {
        when(purchaseRepository.findByUserIdAndStatus(USER_ID, PurchaseStatus.ACTIVE))
                .thenReturn(List.of(purchase(20L, PurchaseType.ADD_ON, CatalogProducts.QR_BRANCH)));

        assertThat(purchaseSelectionPolicy.activePurchaseId(USER_ID)).isNull();
    }

    @Test
    void activePurchaseId_whenUserIsNull_thenReturnNull() {
        assertThat(purchaseSelectionPolicy.activePurchaseId(null)).isNull();
    }

    @Test
    void usablePurchases_whenTrialStartedInIstanbulButNotInUtc_thenTreatItAsStarted() {
        AppTime.setClock(Clock.fixed(
                LocalDateTime.of(2026, 8, 15, 12, 59, 0).atZone(AppTime.ZONE).toInstant(),
                AppTime.ZONE
        ));
        Purchase trial = purchase(102L, PurchaseType.TRIAL, "ULTIMATE_PACKAGE");
        trial.setStartsAt(LocalDateTime.of(2026, 8, 15, 11, 42, 23));
        trial.setExpiresAt(LocalDateTime.of(2026, 9, 14, 11, 42, 23));
        when(purchaseRepository.findByUserIdAndStatus(USER_ID, PurchaseStatus.ACTIVE)).thenReturn(List.of(trial));

        assertThat(purchaseSelectionPolicy.usablePurchases(USER_ID)).containsExactly(trial);
    }

    @Test
    void usablePurchases_whenTrialHasNotStartedYet_thenExcludeIt() {
        AppTime.setClock(Clock.fixed(
                LocalDateTime.of(2026, 8, 15, 9, 44, 30).atZone(AppTime.ZONE).toInstant(),
                AppTime.ZONE
        ));
        Purchase trial = purchase(102L, PurchaseType.TRIAL, "ULTIMATE_PACKAGE");
        trial.setStartsAt(LocalDateTime.of(2026, 8, 15, 11, 42, 23));
        trial.setExpiresAt(LocalDateTime.of(2026, 9, 14, 11, 42, 23));
        when(purchaseRepository.findByUserIdAndStatus(USER_ID, PurchaseStatus.ACTIVE)).thenReturn(List.of(trial));

        assertThat(purchaseSelectionPolicy.usablePurchases(USER_ID)).isEmpty();
    }

    @Test
    void usableAddons_whenMixedPurchases_thenReturnAddonsOnly() {
        Purchase paid = purchase(10L, PurchaseType.PAID, "ULTIMATE_PACKAGE");
        Purchase addon = purchase(20L, PurchaseType.ADD_ON, CatalogProducts.QR_BRANCH);
        when(purchaseRepository.findByUserIdAndStatus(USER_ID, PurchaseStatus.ACTIVE))
                .thenReturn(List.of(paid, addon));

        assertThat(purchaseSelectionPolicy.usableAddons(USER_ID)).containsExactly(addon);
    }

    private Purchase purchase(Long id, PurchaseType type, String packageCode) {
        return Purchase.builder()
                .id(id)
                .userId(USER_ID)
                .packageId(4L)
                .packageCode(packageCode)
                .purchaseType(type)
                .status(PurchaseStatus.ACTIVE)
                .startsAt(LocalDateTime.now().minusDays(1))
                .expiresAt(LocalDateTime.now().plusDays(20))
                .build();
    }
}
