package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogPackages;
import com.ael.algoryqrservice.client.PaymentServiceClient;
import com.ael.algoryqrservice.config.AppProperties;
import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.PaymentServiceException;
import com.ael.algoryqrservice.exception.UnauthorizedException;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.enums.PaymentStyle;
import com.ael.algoryqrservice.model.enums.PurchaseCancellationReason;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.model.enums.SubscriptionStatus;
import com.ael.algoryqrservice.repository.PaymentEventInboxRepository;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseServiceCancelTest {

    @Mock
    private PlanPackageService planPackageService;
    @Mock
    private PurchaseRepository purchaseRepository;
    @Mock
    private PurchaseLogService purchaseLogService;
    @Mock
    private EntitlementService entitlementService;
    @Mock
    private PaymentServiceClient paymentServiceClient;
    @Mock
    private PaymentRequestMapper paymentRequestMapper;
    @Mock
    private AppProperties appProperties;
    @Mock
    private PaymentEventInboxRepository paymentEventInboxRepository;
    @Mock
    private PackageActivationService packageActivationService;
    @Mock
    private PurchaseFulfillmentService purchaseFulfillmentService;
    @Mock
    private PlanChangeService planChangeService;
    @Mock
    private BillingAddressService billingAddressService;
    @Mock
    private MenuPublicAccessService menuPublicAccessService;

    @InjectMocks
    private PurchaseService purchaseService;

    private Purchase purchase;

    @BeforeEach
    void setUp() {
        purchase = Purchase.builder()
                .id(10L)
                .userId(20L)
                .packageId(30L)
                .packageCode(CatalogPackages.PRO_PACKAGE)
                .packageName("PRO")
                .price(new BigDecimal("100.00"))
                .currency("TRY")
                .purchaseType(PurchaseType.PAID)
                .paymentStyle(PaymentStyle.ONE_TIME)
                .status(PurchaseStatus.ACTIVE)
                .startsAt(LocalDateTime.now().minusDays(1))
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();
    }

    @Test
    void cancelMyPurchase_whenActiveOwned_thenCancelAndRevoke() {
        when(purchaseRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(purchase));
        when(purchaseRepository.save(any(Purchase.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = purchaseService.cancelMyPurchase(10L, 20L);

        assertThat(response.getStatus()).isEqualTo(PurchaseStatus.CANCELLED);
        assertThat(purchase.getCancellationReason()).isEqualTo(PurchaseCancellationReason.MANUAL);
        assertThat(purchase.getExpiresAt()).isBeforeOrEqualTo(LocalDateTime.now().plusSeconds(1));
        verify(entitlementService).revokeForCancelledPurchase(purchase);
        verify(menuPublicAccessService).deactivateActiveMenusForUser(20L);
        verify(purchaseFulfillmentService).cancelOpenFulfillments(10L);
        verify(packageActivationService).ensureFreePackage(20L);
        verify(menuPublicAccessService).syncForUser(20L);
        verify(paymentServiceClient, never()).cancelSubscription(any(), any());
    }

    @Test
    void cancelMyPurchase_whenSubscription_thenCancelRemoteFirst() {
        purchase.setPaymentStyle(PaymentStyle.SUBSCRIPTION);
        purchase.setSubscriptionId("sub-1");
        purchase.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
        when(purchaseRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(purchase));
        when(purchaseRepository.save(any(Purchase.class))).thenAnswer(invocation -> invocation.getArgument(0));

        purchaseService.cancelMyPurchase(10L, 20L);

        verify(paymentServiceClient).cancelSubscription(20L, "sub-1");
        assertThat(purchase.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(purchase.getStatus()).isEqualTo(PurchaseStatus.CANCELLED);
    }

    @Test
    void cancelMyPurchase_whenSubscriptionCancelFails_thenKeepActive() {
        purchase.setPaymentStyle(PaymentStyle.SUBSCRIPTION);
        purchase.setSubscriptionId("sub-1");
        when(purchaseRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(purchase));
        when(paymentServiceClient.cancelSubscription(20L, "sub-1"))
                .thenThrow(new PaymentServiceException("Abonelik iptal edilemedi: 500"));

        assertThatThrownBy(() -> purchaseService.cancelMyPurchase(10L, 20L))
                .isInstanceOf(PaymentServiceException.class);

        assertThat(purchase.getStatus()).isEqualTo(PurchaseStatus.ACTIVE);
        verify(purchaseRepository, never()).save(any());
        verify(entitlementService, never()).revokeForCancelledPurchase(any());
    }

    @Test
    void cancelMyPurchase_whenSubscriptionWithoutId_thenReject() {
        purchase.setPaymentStyle(PaymentStyle.SUBSCRIPTION);
        purchase.setSubscriptionId(null);
        when(purchaseRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(purchase));

        assertThatThrownBy(() -> purchaseService.cancelMyPurchase(10L, 20L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("guvenli");

        verify(paymentServiceClient, never()).cancelSubscription(any(), any());
        verify(purchaseRepository, never()).save(any());
    }

    @Test
    void cancelMyPurchase_whenFreePackage_thenReject() {
        purchase.setPackageCode(CatalogPackages.FREE_PACKAGE);
        purchase.setPurchaseType(PurchaseType.FREE);
        purchase.setPrice(BigDecimal.ZERO);
        when(purchaseRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(purchase));

        assertThatThrownBy(() -> purchaseService.cancelMyPurchase(10L, 20L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("iptal edilemez");

        verify(purchaseRepository, never()).save(any());
    }

    @Test
    void cancelMyPurchase_whenOtherUser_thenUnauthorized() {
        when(purchaseRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(purchase));

        assertThatThrownBy(() -> purchaseService.cancelMyPurchase(10L, 99L))
                .isInstanceOf(UnauthorizedException.class);

        verify(purchaseRepository, never()).save(any());
    }

    @Test
    void cancelMyPurchase_whenPending_thenCancelWithoutMenuDeactivate() {
        purchase.setStatus(PurchaseStatus.PENDING);
        when(purchaseRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(purchase));
        when(purchaseRepository.save(any(Purchase.class))).thenAnswer(invocation -> invocation.getArgument(0));

        purchaseService.cancelMyPurchase(10L, 20L);

        ArgumentCaptor<Purchase> captor = ArgumentCaptor.forClass(Purchase.class);
        verify(purchaseRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PurchaseStatus.CANCELLED);
        assertThat(captor.getValue().getCancellationReason()).isEqualTo(PurchaseCancellationReason.MANUAL);
        verify(entitlementService, never()).revokeForCancelledPurchase(any());
        verify(menuPublicAccessService, never()).deactivateActiveMenusForUser(any());
        verify(purchaseFulfillmentService).cancelOpenFulfillments(eq(10L));
        verify(packageActivationService).ensureFreePackage(20L);
    }
}
