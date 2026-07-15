package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.client.PaymentServiceClient;
import com.ael.algoryqrservice.config.AppProperties;
import com.ael.algoryqrservice.exception.InvalidPaymentEventException;
import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.dto.PaymentCompletedEventDto;
import com.ael.algoryqrservice.model.enums.PackageCode;
import com.ael.algoryqrservice.model.enums.PaymentMode;
import com.ael.algoryqrservice.model.enums.PurchaseCancellationReason;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.repository.PaymentEventInboxRepository;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
    private UserPackageService userPackageService;
    @Mock
    private PurchaseFulfillmentService purchaseFulfillmentService;

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
                .packageCode(PackageCode.PRO_PACKAGE)
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
                .code(PackageCode.PRO_PACKAGE)
                .name("PRO")
                .price(new BigDecimal("100.00"))
                .currency("TRY")
                .validityDays(60)
                .active(true)
                .build();
        lenient().when(appProperties.getServiceName()).thenReturn("qr-service");
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
