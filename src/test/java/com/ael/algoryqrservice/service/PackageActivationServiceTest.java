package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogPackages;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.service.entitlement.PurchaseExpiryService;
import com.ael.algoryqrservice.service.entitlement.PurchaseSelectionPolicy;
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

    private static final Long USER_ID = 20L;

    @Mock
    private PurchaseRepository purchaseRepository;
    @Mock
    private PurchaseExpiryService purchaseExpiryService;
    @Mock
    private PurchaseSelectionPolicy purchaseSelectionPolicy;
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
        Purchase trial = subscription(102L, CatalogPackages.ULTIMATE_PACKAGE, PurchaseType.TRIAL);
        when(purchaseSelectionPolicy.usableSubscriptions(USER_ID)).thenReturn(List.of(trial));
        when(purchaseSelectionPolicy.highestPriority(List.of(trial))).thenReturn(Optional.of(trial));

        Optional<Purchase> result = packageActivationService.ensureSubscriptionState(USER_ID);

        assertThat(result).contains(trial);
        verify(purchaseExpiryService).expireDueForUser(USER_ID);
        verify(menuPublicAccessService).syncForUser(USER_ID);
    }

    @Test
    void ensureSubscriptionState_whenNoUsableSubscription_thenReturnEmpty() {
        when(purchaseSelectionPolicy.usableSubscriptions(USER_ID)).thenReturn(List.of());
        when(purchaseSelectionPolicy.highestPriority(List.of())).thenReturn(Optional.empty());

        Optional<Purchase> result = packageActivationService.ensureSubscriptionState(USER_ID);

        assertThat(result).isEmpty();
        verify(menuPublicAccessService).syncForUser(USER_ID);
    }

    @Test
    void ensureSubscriptionState_whenAddonAndPaidExist_thenReturnPaid() {
        Purchase paid = subscription(1L, CatalogPackages.ULTIMATE_PACKAGE, PurchaseType.PAID);
        when(purchaseSelectionPolicy.usableSubscriptions(USER_ID)).thenReturn(List.of(paid));
        when(purchaseSelectionPolicy.highestPriority(List.of(paid))).thenReturn(Optional.of(paid));

        Optional<Purchase> result = packageActivationService.ensureSubscriptionState(USER_ID);

        assertThat(result).contains(paid);
    }

    @Test
    void syncSubscriptionStateForUsers_whenDuplicateIds_thenSyncEachUserOnce() {
        when(purchaseSelectionPolicy.usableSubscriptions(USER_ID)).thenReturn(List.of());
        when(purchaseSelectionPolicy.highestPriority(List.of())).thenReturn(Optional.empty());

        packageActivationService.syncSubscriptionStateForUsers(List.of(USER_ID, USER_ID));

        verify(menuPublicAccessService).syncForUser(USER_ID);
    }

    @Test
    void syncSubscriptionStateForUsers_whenEmpty_thenDoNothing() {
        packageActivationService.syncSubscriptionStateForUsers(List.of());

        verify(purchaseExpiryService, never()).expireDueForUser(any());
        verify(menuPublicAccessService, never()).syncForUser(any());
    }

    @Test
    void activatePurchasedPackage_whenAnotherActiveExists_thenSupersedePrevious() {
        Purchase freePurchase = activePurchase(1L, CatalogPackages.FREE_PACKAGE, PurchaseType.FREE);
        Purchase proPurchase = activePurchase(2L, CatalogPackages.PRO_PACKAGE, PurchaseType.PAID);
        when(purchaseRepository.findByUserIdAndStatus(USER_ID, PurchaseStatus.ACTIVE))
                .thenReturn(List.of(freePurchase, proPurchase));

        packageActivationService.activatePurchasedPackage(proPurchase);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Purchase>> captor = ArgumentCaptor.forClass(List.class);
        verify(purchaseRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).singleElement().satisfies(superseded -> {
            assertThat(superseded.getId()).isEqualTo(1L);
            assertThat(superseded.getStatus()).isEqualTo(PurchaseStatus.SUPERSEDED);
        });
        verify(menuPublicAccessService).syncForUser(USER_ID);
    }

    @Test
    void activatePurchasedPackage_whenAddon_thenKeepExistingActive() {
        Purchase addon = activePurchase(2L, "QR_MENU", PurchaseType.ADD_ON);

        packageActivationService.activatePurchasedPackage(addon);

        verify(purchaseRepository, never()).saveAll(any());
        verify(menuPublicAccessService).syncForUser(USER_ID);
    }

    private static Purchase activePurchase(Long id, String packageCode, PurchaseType type) {
        return Purchase.builder()
                .id(id)
                .userId(USER_ID)
                .packageCode(packageCode)
                .purchaseType(type)
                .status(PurchaseStatus.ACTIVE)
                .build();
    }

    private static Purchase subscription(Long id, String packageCode, PurchaseType type) {
        Purchase purchase = activePurchase(id, packageCode, type);
        purchase.setPackageId(4L);
        purchase.setStartsAt(LocalDateTime.of(2026, 8, 15, 11, 0, 0));
        purchase.setExpiresAt(LocalDateTime.of(2026, 9, 14, 11, 0, 0));
        return purchase;
    }
}
