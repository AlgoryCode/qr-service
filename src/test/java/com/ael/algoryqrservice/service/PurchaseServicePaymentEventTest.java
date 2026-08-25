package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.client.PaymentServiceClient;
import com.ael.algoryqrservice.client.dto.BillingPaymentDtos;
import com.ael.algoryqrservice.client.dto.PaymentCheckoutFormRequest;
import com.ael.algoryqrservice.client.dto.PaymentCheckoutFormResponse;
import com.ael.algoryqrservice.config.AppProperties;
import com.ael.algoryqrservice.config.BillingSubscriptionProperties;
import com.ael.algoryqrservice.config.PaymentClientProperties;
import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.InvalidPaymentEventException;
import com.ael.algoryqrservice.exception.PaymentServiceException;
import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.dto.PaymentCompletedEventDto;
import com.ael.algoryqrservice.model.dto.PurchaseFulfillmentResponse;
import com.ael.algoryqrservice.model.dto.PurchaseInitiateResponse;
import com.ael.algoryqrservice.model.dto.PurchaseRequest;
import com.ael.algoryqrservice.catalog.CatalogPackages;
import com.ael.algoryqrservice.model.enums.BillingPeriod;
import com.ael.algoryqrservice.model.enums.PaymentMode;
import com.ael.algoryqrservice.model.enums.PaymentStyle;
import com.ael.algoryqrservice.model.enums.PurchaseCancellationReason;
import com.ael.algoryqrservice.model.enums.PurchaseLogAction;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.model.enums.RefundStatus;
import com.ael.algoryqrservice.model.enums.SubscriptionStatus;
import com.ael.algoryqrservice.repository.PaymentEventInboxRepository;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.ael.algoryqrservice.service.entitlement.PackageEntitlementWriter;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseServicePaymentEventTest {

    @Mock
    private PlanPackageService planPackageService;
    @Mock
    private PurchaseRepository purchaseRepository;
    @Mock
    private PurchaseLogService purchaseLogService;
    @Mock
    private PackageEntitlementWriter entitlementWriter;
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
    private BillingAddressService billingAddressService;
    @Mock
    private MenuPublicAccessService menuPublicAccessService;
    @Mock
    private PlanChangeService planChangeService;
    @Mock
    private SubscriptionRefundPolicy subscriptionRefundPolicy;
    @Mock
    private BillingSubscriptionProperties billingSubscriptionProperties;
    @Mock
    private PaymentClientProperties paymentClientProperties;
    @Mock
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @InjectMocks
    private PurchaseService purchaseService;

    private Purchase purchase;
    private PlanPackage planPackage;

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
                .paymentConversationId("conversation-10")
                .paymentMode(PaymentMode.THREE_DS)
                .installmentCount(2)
                .status(PurchaseStatus.PENDING)
                .build();
        planPackage = PlanPackage.builder()
                .id(30L)
                .code(CatalogPackages.PRO_PACKAGE)
                .name("PRO")
                .price(new BigDecimal("100.00"))
                .currency("TRY")
                .validityDays(60)
                .active(true)
                .build();
        lenient().when(appProperties.getServiceName()).thenReturn("qr-service");
        lenient().when(billingSubscriptionProperties.getManualPaymentGraceDays()).thenReturn(7);
    }

    @Test
    void handlePaymentSuccess_whenEventAlreadyProcessed_thenRemainIdempotent() {
        PaymentCompletedEventDto event = paidEvent();
        when(paymentEventInboxRepository.existsByEventId(event.getEventId())).thenReturn(true);

        purchaseService.handlePaymentSuccess(event);

        verify(purchaseRepository, never()).findByIdForUpdate(any());
        verify(purchaseFulfillmentService, never()).fulfillPaidInstallment(any(), any(), any(), any());
    }

    @Test
    void handlePaymentSuccess_whenAmountMismatch_thenRejectWithoutFulfillment() {
        PaymentCompletedEventDto event = paidEvent();
        event.setAmount(new BigDecimal("49.99"));
        when(purchaseRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(purchase));

        assertThatThrownBy(() -> purchaseService.handlePaymentSuccess(event))
                .isInstanceOf(InvalidPaymentEventException.class)
                .hasMessageContaining("amount");

        verify(purchaseFulfillmentService, never()).fulfillPaidInstallment(any(), any(), any(), any());
        verify(paymentEventInboxRepository, never()).save(any());
    }

    @Test
    void handlePaymentSuccess_whenUserMismatch_thenRejectWithoutFulfillment() {
        PaymentCompletedEventDto event = paidEvent();
        event.getSourceMetadata().put("userId", 99L);
        when(purchaseRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(purchase));

        assertThatThrownBy(() -> purchaseService.handlePaymentSuccess(event))
                .isInstanceOf(InvalidPaymentEventException.class)
                .hasMessageContaining("metadata");

        verify(purchaseFulfillmentService, never()).fulfillPaidInstallment(any(), any(), any(), any());
    }

    @Test
    void handlePaymentSuccess_whenTimeoutCancelled_thenFulfillLateSuccess() {
        PaymentCompletedEventDto event = paidEvent();
        purchase.setStatus(PurchaseStatus.CANCELLED);
        purchase.setCancellationReason(PurchaseCancellationReason.PAYMENT_TIMEOUT);
        when(purchaseRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(purchase));
        when(planPackageService.findPackage(30L)).thenReturn(planPackage);

        purchaseService.handlePaymentSuccess(event);

        verify(purchaseFulfillmentService).fulfillPaidInstallment(
                org.mockito.ArgumentMatchers.eq(purchase),
                org.mockito.ArgumentMatchers.eq(planPackage),
                org.mockito.ArgumentMatchers.eq(event),
                any()
        );
        verify(paymentEventInboxRepository).save(any());
    }

    @Test
    void handlePaymentSuccess_whenManuallyCancelled_thenReject() {
        PaymentCompletedEventDto event = paidEvent();
        purchase.setStatus(PurchaseStatus.CANCELLED);
        purchase.setCancellationReason(PurchaseCancellationReason.MANUAL);
        when(purchaseRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(purchase));

        assertThatThrownBy(() -> purchaseService.handlePaymentSuccess(event))
                .isInstanceOf(InvalidPaymentEventException.class)
                .hasMessageContaining("cancelled");

        verify(purchaseFulfillmentService, never()).fulfillPaidInstallment(any(), any(), any(), any());
    }

    @Test
    void handlePaymentSuccess_whenOneTimeEventMissingInstallmentNumber_thenFulfill() {
        PaymentCompletedEventDto event = oneTimePaidEvent();
        purchase.setInstallmentCount(1);
        purchase.setPaymentStyle(com.ael.algoryqrservice.model.enums.PaymentStyle.ONE_TIME);
        when(purchaseRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(purchase));
        when(planPackageService.findPackage(30L)).thenReturn(planPackage);

        purchaseService.handlePaymentSuccess(event);

        verify(purchaseFulfillmentService).fulfillPaidInstallment(
                org.mockito.ArgumentMatchers.eq(purchase),
                org.mockito.ArgumentMatchers.eq(planPackage),
                org.mockito.ArgumentMatchers.eq(event),
                any()
        );
        verify(paymentEventInboxRepository).save(any());
    }

    @Test
    void handlePaymentFailed_whenPending_thenMarkFailed() {
        PaymentCompletedEventDto event = failedEvent();
        purchase.setInstallmentCount(1);
        when(purchaseRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(purchase));

        purchaseService.handlePaymentFailed(event);

        verify(purchaseRepository).save(purchase);
        verify(purchaseFulfillmentService).recordUnpaidInstallment(
                org.mockito.ArgumentMatchers.eq(purchase),
                org.mockito.ArgumentMatchers.eq(event),
                any(),
                org.mockito.ArgumentMatchers.eq(com.ael.algoryqrservice.model.enums.FulfillmentStatus.FAILED)
        );
        verify(paymentEventInboxRepository).save(any());
        org.assertj.core.api.Assertions.assertThat(purchase.getStatus()).isEqualTo(PurchaseStatus.FAILED);
    }

    @Test
    void handlePaymentFailed_whenActiveSubscription_thenMarkPastDue() {
        PaymentCompletedEventDto event = failedEvent();
        purchase.setInstallmentCount(1);
        purchase.setStatus(PurchaseStatus.ACTIVE);
        purchase.setPaymentStyle(PaymentStyle.SUBSCRIPTION);
        when(purchaseRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(purchase));
        when(purchaseRepository.save(purchase)).thenReturn(purchase);

        purchaseService.handlePaymentFailed(event);

        verify(purchaseRepository).save(purchase);
        verify(purchaseFulfillmentService, never()).recordUnpaidInstallment(any(), any(), any(), any());
        verify(paymentEventInboxRepository).save(any());
        org.assertj.core.api.Assertions.assertThat(purchase.getStatus()).isEqualTo(PurchaseStatus.ACTIVE);
        org.assertj.core.api.Assertions.assertThat(purchase.getSubscriptionStatus())
                .isEqualTo(SubscriptionStatus.PAST_DUE);
        assertThat(purchase.getSubscriptionGraceEndsAt()).isAfter(LocalDateTime.now().plusDays(6));
    }

    @Test
    void handleSubscriptionPastDue_whenSubscription_thenSetGraceWindow() {
        PaymentCompletedEventDto event = pastDueEvent();
        purchase.setStatus(PurchaseStatus.ACTIVE);
        purchase.setPaymentStyle(PaymentStyle.SUBSCRIPTION);
        when(purchaseRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(purchase));
        when(purchaseRepository.save(purchase)).thenReturn(purchase);

        purchaseService.handleSubscriptionPastDue(event);

        assertThat(purchase.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
        assertThat(purchase.getSubscriptionGraceEndsAt()).isAfter(LocalDateTime.now().plusDays(6));
        verify(purchaseLogService).log(
                eq(10L),
                eq(20L),
                eq(PurchaseLogAction.PURCHASE_PAYMENT_FAILED),
                any()
        );
        verify(paymentEventInboxRepository).save(any());
    }

    @Test
    void paySubscriptionDebt_whenPastDue_thenInitializeCheckoutForm() {
        User user = User.builder().id(20L).firstName("A").email("a@test.com").build();
        purchase.setStatus(PurchaseStatus.ACTIVE);
        purchase.setPaymentStyle(PaymentStyle.SUBSCRIPTION);
        purchase.setSubscriptionStatus(SubscriptionStatus.PAST_DUE);
        purchase.setSubscriptionGraceEndsAt(LocalDateTime.now().plusDays(5));
        purchase.setPaymentMethodId(null);
        when(purchaseRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(purchase));
        when(purchaseRepository.save(purchase)).thenReturn(purchase);
        when(planPackageService.findPackage(30L)).thenReturn(planPackage);
        when(purchaseFulfillmentService.getFulfillments(10L)).thenReturn(List.of(
                PurchaseFulfillmentResponse.builder().installmentNumber(1).build()
        ));
        when(paymentRequestMapper.newPaymentAttemptId(anyLong())).thenReturn("debt-conversation-10");
        PaymentCheckoutFormRequest checkoutRequest = PaymentCheckoutFormRequest.builder().build();
        when(paymentRequestMapper.toDebtCheckoutFormRequest(
                eq(purchase),
                eq(user),
                eq(planPackage),
                eq("127.0.0.1"),
                eq(appProperties),
                any(),
                eq("debt-conversation-10"),
                eq(2)
        )).thenReturn(checkoutRequest);
        PaymentCheckoutFormResponse checkoutResponse = new PaymentCheckoutFormResponse();
        checkoutResponse.setConversationId("debt-conversation-10");
        checkoutResponse.setToken("cf-token");
        checkoutResponse.setPaymentPageUrl("https://pay.example/cf");
        when(paymentServiceClient.initializeCheckoutForm(20L, checkoutRequest)).thenReturn(checkoutResponse);

        PurchaseInitiateResponse response = purchaseService.paySubscriptionDebt(user, 10L, "127.0.0.1");

        assertThat(response.getToken()).isEqualTo("cf-token");
        assertThat(response.getPaymentPageUrl()).isEqualTo("https://pay.example/cf");
        assertThat(purchase.getPaymentMode()).isEqualTo(PaymentMode.CHECKOUT_FORM);
        assertThat(purchase.getCurrentPeriodConversationId()).isEqualTo("debt-conversation-10");
        verify(purchaseLogService).log(
                eq(10L),
                eq(20L),
                eq(PurchaseLogAction.PURCHASE_DEBT_PAYMENT_STARTED),
                any()
        );
    }

    @Test
    void paySubscriptionDebt_whenAutoRenewWithCard_thenReject() {
        User user = User.builder().id(20L).firstName("A").email("a@test.com").build();
        purchase.setStatus(PurchaseStatus.ACTIVE);
        purchase.setPaymentStyle(PaymentStyle.SUBSCRIPTION);
        purchase.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
        purchase.setPaymentMethodId(42L);
        purchase.setCancelAtPeriodEnd(false);
        when(purchaseRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(purchase));

        assertThatThrownBy(() -> purchaseService.paySubscriptionDebt(user, 10L, "127.0.0.1"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("manuel borc");
    }

    @Test
    void handlePaymentSuccess_whenSubscriptionCycle_thenAcceptsFullPrice() {
        purchase.setInstallmentCount(12);
        purchase.setPaymentStyle(PaymentStyle.SUBSCRIPTION);
        purchase.setPrice(new BigDecimal("100.00"));
        when(purchaseRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(purchase));
        when(planPackageService.findPackage(30L)).thenReturn(planPackage);

        PaymentCompletedEventDto event = new PaymentCompletedEventDto();
        event.setEventId("event-sub-2");
        event.setEventType("payment.subscription.paid");
        event.setPaymentId("payment-cycle-2");
        event.setConversationId("sub-1-cycle-2");
        event.setServiceName("qr-service");
        event.setSourceReferenceId("10");
        event.setBillingCycleNumber(2);
        event.setAmount(new BigDecimal("100.00"));
        event.setCurrency("TRY");
        event.setPeriodStart("2026-08-20");
        event.setPeriodEnd("2026-09-19");
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("purchaseId", 10L);
        metadata.put("userId", 20L);
        metadata.put("packageId", 30L);
        metadata.put("packageCode", "PRO_PACKAGE");
        metadata.put("purchaseConversationId", "conversation-10");
        metadata.put("installmentCount", 12);
        event.setSourceMetadata(metadata);

        purchaseService.handlePaymentSuccess(event);

        verify(purchaseFulfillmentService).fulfillPaidInstallment(
                eq(purchase),
                eq(planPackage),
                eq(event),
                any()
        );
        verify(paymentEventInboxRepository).save(any());
    }

    @Test
    void reconcilePaidPendingPurchase_whenPaymentSuccess_thenActivate() {
        purchase.setInstallmentCount(1);
        purchase.setPaymentStyle(PaymentStyle.ONE_TIME);
        when(purchaseRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(purchase));
        when(paymentServiceClient.findPayment(20L, "conversation-10"))
                .thenReturn(Optional.of(new BillingPaymentDtos.RefundablePayment(
                        "conversation-10",
                        "payment-99",
                        "tx-99",
                        "SUCCESS",
                        new BigDecimal("100.00"),
                        BigDecimal.ZERO,
                        new BigDecimal("100.00")
                )));
        when(planPackageService.findPackage(30L)).thenReturn(planPackage);

        boolean activated = purchaseService.reconcilePaidPendingPurchase(10L);

        assertThat(activated).isTrue();
        ArgumentCaptor<PaymentCompletedEventDto> eventCaptor = ArgumentCaptor.forClass(PaymentCompletedEventDto.class);
        verify(purchaseFulfillmentService).fulfillPaidInstallment(
                eq(purchase),
                eq(planPackage),
                eventCaptor.capture(),
                any()
        );
        assertThat(eventCaptor.getValue().getEventId()).startsWith("reconcile:conversation-10:");
        assertThat(eventCaptor.getValue().getAmount()).isEqualByComparingTo("100.00");
        verify(paymentEventInboxRepository).save(any());
    }

    @Test
    void reconcilePaidPendingPurchase_whenPaymentNotSuccess_thenSkip() {
        purchase.setInstallmentCount(1);
        when(purchaseRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(purchase));
        when(paymentServiceClient.findPayment(20L, "conversation-10"))
                .thenReturn(Optional.of(new BillingPaymentDtos.RefundablePayment(
                        "conversation-10",
                        null,
                        null,
                        "PENDING",
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO
                )));

        boolean activated = purchaseService.reconcilePaidPendingPurchase(10L);

        assertThat(activated).isFalse();
        verify(purchaseFulfillmentService, never()).fulfillPaidInstallment(any(), any(), any(), any());
    }

    @Test
    void cancelExpiredPendingPurchases_whenPaymentAlreadySuccess_thenActivateInsteadOfCancel() {
        purchase.setInstallmentCount(1);
        purchase.setPaymentStyle(PaymentStyle.ONE_TIME);
        purchase.setPurchasedAt(LocalDateTime.now().minusHours(2));
        when(purchaseRepository.findByStatusAndPurchasedAtBefore(eq(PurchaseStatus.PENDING), any()))
                .thenReturn(List.of(purchase));
        when(purchaseRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(purchase));
        when(paymentServiceClient.findPayment(20L, "conversation-10"))
                .thenReturn(Optional.of(new BillingPaymentDtos.RefundablePayment(
                        "conversation-10",
                        "payment-99",
                        "tx-99",
                        "SUCCESS",
                        new BigDecimal("100.00"),
                        BigDecimal.ZERO,
                        new BigDecimal("100.00")
                )));
        when(planPackageService.findPackage(30L)).thenReturn(planPackage);

        purchaseService.cancelExpiredPendingPurchases(30);

        verify(purchaseFulfillmentService).fulfillPaidInstallment(eq(purchase), eq(planPackage), any(), any());
        verify(purchaseRepository, never()).save(purchase);
        assertThat(purchase.getCancellationReason()).isNull();
    }

    @Test
    void reconcilePaidPendingPurchase_whenPaymentNotFound_thenSkip() {
        when(purchaseRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(purchase));
        when(paymentServiceClient.findPayment(20L, "conversation-10")).thenReturn(Optional.empty());

        boolean activated = purchaseService.reconcilePaidPendingPurchase(10L);

        assertThat(activated).isFalse();
        verify(paymentServiceClient, never()).getRefundablePayment(any(), any());
        verify(purchaseFulfillmentService, never()).fulfillPaidInstallment(any(), any(), any(), any());
    }

    @Test
    void cancelExpiredPendingPurchases_whenPaymentNotFound_thenCancelWithTimeout() {
        purchase.setPurchasedAt(LocalDateTime.now().minusHours(2));
        when(purchaseRepository.findByStatusAndPurchasedAtBefore(eq(PurchaseStatus.PENDING), any()))
                .thenReturn(List.of(purchase));
        when(purchaseRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(purchase));
        when(purchaseRepository.save(purchase)).thenReturn(purchase);
        when(paymentServiceClient.findPayment(20L, "conversation-10")).thenReturn(Optional.empty());

        purchaseService.cancelExpiredPendingPurchases(30);

        assertThat(purchase.getStatus()).isEqualTo(PurchaseStatus.CANCELLED);
        assertThat(purchase.getCancellationReason()).isEqualTo(PurchaseCancellationReason.PAYMENT_TIMEOUT);
        verify(planChangeService).onPurchasePaymentFailed(purchase);
        verify(purchaseFulfillmentService, never()).fulfillPaidInstallment(any(), any(), any(), any());
    }

    @Test
    void handlePaymentRefunded_whenActiveSubscription_thenCancelAndRevoke() {
        PaymentCompletedEventDto event = refundedEvent();
        purchase.setStatus(PurchaseStatus.ACTIVE);
        purchase.setPaymentStyle(PaymentStyle.SUBSCRIPTION);
        purchase.setSubscriptionId("sub-1");
        purchase.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
        purchase.setPurchaseType(PurchaseType.PAID);
        when(purchaseRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(purchase));
        when(purchaseRepository.save(purchase)).thenReturn(purchase);

        purchaseService.handlePaymentRefunded(event);

        assertThat(purchase.getStatus()).isEqualTo(PurchaseStatus.CANCELLED);
        assertThat(purchase.getRefundStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(purchase.getRefundedAt()).isNotNull();
        verify(paymentServiceClient).cancelSubscription(20L, "sub-1");
        verify(entitlementWriter).revokeForCancelledPurchase(purchase);
        verify(menuPublicAccessService).deactivateActiveMenusForUser(20L);
        verify(paymentEventInboxRepository).save(any());
    }

    @Test
    void purchase_whenCheckoutInitFails_thenFailedWithoutConversationId() {
        User user = User.builder().id(20L).firstName("A").email("a@test.com").build();
        PurchaseRequest request = new PurchaseRequest();
        request.setPackageId(30L);
        request.setPaymentMode(PaymentMode.CHECKOUT_FORM);
        request.setBillingPeriod(BillingPeriod.MONTHLY);
        request.setBillingAddressId(1L);
        planPackage.setPurchasable(true);
        planPackage.setSystemManaged(false);
        planPackage.setPrice(new BigDecimal("100.00"));
        planPackage.setYearlyPrice(new BigDecimal("1000.00"));
        when(planPackageService.findActivePackage(30L)).thenReturn(planPackage);
        when(billingAddressService.resolveSnapshot(eq(20L), eq(1L), any())).thenReturn(null);
        when(purchaseRepository.save(any(Purchase.class))).thenAnswer(invocation -> {
            Purchase saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(10L);
            }
            return saved;
        });
        when(paymentRequestMapper.newPaymentAttemptId(anyLong())).thenReturn("conversation-10");
        when(paymentRequestMapper.toDebtCheckoutFormRequest(
                any(), eq(user), eq(planPackage), eq("127.0.0.1"), eq(appProperties), eq(paymentClientProperties),
                eq("conversation-10"), eq(1)
        )).thenReturn(PaymentCheckoutFormRequest.builder().build());
        when(paymentServiceClient.initializeCheckoutForm(eq(20L), any()))
                .thenThrow(new PaymentServiceException("Checkout basarisiz"));

        assertThatThrownBy(() -> purchaseService.purchase(user, request, "127.0.0.1"))
                .isInstanceOf(PaymentServiceException.class);

        ArgumentCaptor<Purchase> captor = ArgumentCaptor.forClass(Purchase.class);
        verify(purchaseRepository, atLeastOnce()).save(captor.capture());
        Purchase lastSaved = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertThat(lastSaved.getStatus()).isEqualTo(PurchaseStatus.FAILED);
        assertThat(lastSaved.getPaymentConversationId()).isNull();
    }

    private PaymentCompletedEventDto refundedEvent() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("purchaseId", 10L);
        metadata.put("userId", 20L);
        metadata.put("packageId", 30L);
        metadata.put("packageCode", "PRO_PACKAGE");
        metadata.put("purchaseConversationId", "conversation-10");
        metadata.put("installmentCount", 1);
        metadata.put("installmentNumber", 1);

        PaymentCompletedEventDto event = new PaymentCompletedEventDto();
        event.setEventId("event-refunded");
        event.setEventType("payment.refunded");
        event.setPaymentId("payment-refund");
        event.setConversationId("conversation-10");
        event.setServiceName("qr-service");
        event.setSourceReferenceId("10");
        event.setSourceMetadata(metadata);
        event.setAmount(new BigDecimal("100.00"));
        event.setCurrency("TRY");
        event.setPeriodStart("2026-07-16");
        event.setPeriodEnd("2026-08-14");
        return event;
    }

    private PaymentCompletedEventDto pastDueEvent() {
        PaymentCompletedEventDto event = new PaymentCompletedEventDto();
        event.setEventId("event-past-due");
        event.setEventType("payment.subscription.past_due");
        event.setPaymentId("payment-past-due");
        event.setConversationId("conversation-10");
        event.setServiceName("qr-service");
        event.setSourceReferenceId("10");
        event.setPurchaseId("10");
        event.setAmount(new BigDecimal("100.00"));
        event.setCurrency("TRY");
        return event;
    }

    private PaymentCompletedEventDto failedEvent() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("purchaseId", 10L);
        metadata.put("userId", 20L);
        metadata.put("packageId", 30L);
        metadata.put("packageCode", "PRO_PACKAGE");
        metadata.put("installmentCount", 1);
        metadata.put("purchaseConversationId", "conversation-10");

        PaymentCompletedEventDto event = new PaymentCompletedEventDto();
        event.setEventId("event-failed");
        event.setEventType("payment.failed");
        event.setPaymentId("payment-fail");
        event.setConversationId("conversation-10");
        event.setServiceName("qr-service");
        event.setSourceReferenceId("10");
        event.setSourceMetadata(metadata);
        event.setAmount(new BigDecimal("100.00"));
        event.setCurrency("TRY");
        event.setPeriodStart("2026-07-16");
        event.setPeriodEnd("2026-08-14");
        event.setFailureReason("MD_STATUS_FAILED");
        return event;
    }

    private PaymentCompletedEventDto oneTimePaidEvent() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("purchaseId", 10L);
        metadata.put("userId", 20L);
        metadata.put("packageId", 30L);
        metadata.put("packageCode", "PRO_PACKAGE");
        metadata.put("installmentCount", 1);
        metadata.put("purchaseConversationId", "conversation-10");

        PaymentCompletedEventDto event = new PaymentCompletedEventDto();
        event.setEventId("event-one-time");
        event.setEventType("payment.success");
        event.setPaymentId("payment-19");
        event.setConversationId("conversation-10");
        event.setServiceName("qr-service");
        event.setSourceReferenceId("10");
        event.setSourceMetadata(metadata);
        event.setAmount(new BigDecimal("100.00"));
        event.setCurrency("TRY");
        event.setPeriodStart("2026-07-16");
        event.setPeriodEnd("2026-08-14");
        return event;
    }

    private PaymentCompletedEventDto paidEvent() {
        LocalDateTime periodStart = LocalDateTime.of(2026, 7, 15, 0, 0);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("purchaseId", 10L);
        metadata.put("userId", 20L);
        metadata.put("packageId", 30L);
        metadata.put("packageCode", "PRO_PACKAGE");
        metadata.put("installmentId", "installment-1");
        metadata.put("installmentNumber", 1);
        metadata.put("installmentCount", 2);
        metadata.put("periodStart", periodStart.toString());
        metadata.put("periodEnd", periodStart.plusDays(30).toString());

        PaymentCompletedEventDto event = new PaymentCompletedEventDto();
        event.setEventId("event-1");
        event.setEventType("payment.installment.paid");
        event.setPaymentId("payment-1");
        event.setConversationId("conversation-10");
        event.setServiceName("qr-service");
        event.setSourceReferenceId("10");
        event.setSourceMetadata(metadata);
        event.setAmount(new BigDecimal("50.00"));
        event.setCurrency("TRY");
        return event;
    }
}
