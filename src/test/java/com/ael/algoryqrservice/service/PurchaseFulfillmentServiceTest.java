package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.PurchaseFulfillment;
import com.ael.algoryqrservice.model.dto.PaymentCompletedEventDto;
import com.ael.algoryqrservice.model.dto.PaymentEventMetadata;
import com.ael.algoryqrservice.model.enums.FulfillmentStatus;
import com.ael.algoryqrservice.catalog.CatalogPackages;
import com.ael.algoryqrservice.model.enums.PaymentStyle;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.repository.PlanPackageRepository;
import com.ael.algoryqrservice.repository.PurchaseFulfillmentRepository;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseFulfillmentServiceTest {

    @Mock
    private PurchaseFulfillmentRepository fulfillmentRepository;
    @Mock
    private PurchaseRepository purchaseRepository;
    @Mock
    private PlanPackageRepository planPackageRepository;
    @Mock
    private EntitlementService entitlementService;
    @Mock
    private PackageActivationService packageActivationService;
    @Mock
    private MenuPublicAccessService menuPublicAccessService;

    @InjectMocks
    private PurchaseFulfillmentService fulfillmentService;

    private Purchase purchase;
    private PlanPackage planPackage;
    private PaymentCompletedEventDto event;
    private PaymentEventMetadata metadata;

    @BeforeEach
    void setUp() {
        purchase = Purchase.builder()
                .id(1L)
                .userId(2L)
                .packageId(3L)
                .packageCode(CatalogPackages.PRO_PACKAGE)
                .packageName("PRO")
                .status(PurchaseStatus.PENDING)
                .build();
        planPackage = PlanPackage.builder()
                .id(3L)
                .code(CatalogPackages.PRO_PACKAGE)
                .name("PRO")
                .items(List.of())
                .build();
        event = new PaymentCompletedEventDto();
        event.setEventId("event-1");
        event.setPaymentId("payment-1");
        metadata = new PaymentEventMetadata(
                1L,
                2L,
                3L,
                CatalogPackages.PRO_PACKAGE,
                "conversation-1",
                "installment-1",
                1,
                2,
                LocalDateTime.of(2026, 7, 1, 0, 0),
                LocalDateTime.of(2026, 8, 1, 0, 0)
        );
    }

    @Test
    void initializeSchedule_whenSubscription_thenCreatesTwelveFullPriceRows() {
        purchase.setPaymentStyle(PaymentStyle.SUBSCRIPTION);
        purchase.setPrice(new BigDecimal("100.00"));
        purchase.setCurrency("TRY");
        purchase.setInstallmentCount(12);
        purchase.setPaymentConversationId("conv-sub");
        purchase.setPurchasedAt(LocalDateTime.of(2026, 7, 20, 10, 0));
        when(fulfillmentRepository.findByPurchaseIdOrderByInstallmentNumberAsc(1L)).thenReturn(List.of());

        fulfillmentService.initializeSchedule(purchase, "qr-service");

        ArgumentCaptor<PurchaseFulfillment> captor = ArgumentCaptor.forClass(PurchaseFulfillment.class);
        verify(fulfillmentRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues()).hasSize(12);
        assertThat(captor.getAllValues().get(0).getDueAt()).isEqualTo(LocalDateTime.of(2026, 7, 20, 10, 0));
        assertThat(captor.getAllValues().get(1).getDueAt()).isEqualTo(LocalDateTime.of(2026, 8, 20, 10, 0));
        assertThat(captor.getAllValues()).allMatch(row ->
                row.getAmount().compareTo(new BigDecimal("100.00")) == 0
                        && row.getStatus() == FulfillmentStatus.PENDING
                        && row.getInstallmentCount() == 12
        );
    }

    @Test
    void fulfillPaidInstallment_whenSubscriptionCycle_thenResolvesByInstallmentNumber() {
        purchase.setPaymentStyle(PaymentStyle.SUBSCRIPTION);
        purchase.setStatus(PurchaseStatus.ACTIVE);
        PurchaseFulfillment cycleTwo = PurchaseFulfillment.builder()
                .purchaseId(1L)
                .installmentId("plan:2")
                .installmentNumber(2)
                .installmentCount(12)
                .status(FulfillmentStatus.PENDING)
                .dueAt(LocalDateTime.of(2026, 8, 20, 10, 0))
                .amount(new BigDecimal("100.00"))
                .currency("TRY")
                .build();
        PaymentEventMetadata cycleMeta = new PaymentEventMetadata(
                1L, 2L, 3L, CatalogPackages.PRO_PACKAGE, "conversation-1",
                "payment-cycle-2", 2, 12,
                LocalDateTime.of(2026, 8, 20, 0, 0),
                LocalDateTime.of(2026, 9, 20, 0, 0)
        );
        when(fulfillmentRepository.findByPurchaseIdAndInstallmentNumber(1L, 2))
                .thenReturn(Optional.of(cycleTwo));
        when(fulfillmentRepository.findByPurchaseIdAndStatusOrderByInstallmentNumberAsc(1L, FulfillmentStatus.PAID))
                .thenReturn(List.of(PurchaseFulfillment.builder().status(FulfillmentStatus.PAID).build()));
        when(purchaseRepository.save(purchase)).thenReturn(purchase);

        fulfillmentService.fulfillPaidInstallment(purchase, planPackage, event, cycleMeta);

        assertThat(cycleTwo.getStatus()).isEqualTo(FulfillmentStatus.PAID);
        verify(fulfillmentRepository).save(cycleTwo);
    }

    @Test
    void fulfillPaidInstallment_whenAlreadyPaid_thenRemainIdempotent() {
        PurchaseFulfillment existing = PurchaseFulfillment.builder()
                .purchaseId(1L)
                .installmentId("installment-1")
                .status(FulfillmentStatus.PAID)
                .build();
        when(fulfillmentRepository.findByPurchaseIdAndInstallmentId(1L, "installment-1"))
                .thenReturn(Optional.of(existing));

        fulfillmentService.fulfillPaidInstallment(purchase, planPackage, event, metadata);

        verify(fulfillmentRepository, never()).save(any());
        verify(purchaseRepository, never()).save(any());
        verify(entitlementService, never()).synchronizePeriod(any());
    }

    @Test
    void fulfillPaidInstallment_whenFirstPayment_thenActivateOnePeriod() {
        when(fulfillmentRepository.findByPurchaseIdAndInstallmentId(1L, "installment-1"))
                .thenReturn(Optional.empty());
        when(fulfillmentRepository.findByPurchaseIdAndStatusOrderByInstallmentNumberAsc(
                1L,
                FulfillmentStatus.PAID
        )).thenReturn(List.of());
        when(planPackageRepository.findByIdWithItems(3L)).thenReturn(Optional.of(planPackage));
        LocalDateTime before = LocalDateTime.now();

        fulfillmentService.fulfillPaidInstallment(purchase, planPackage, event, metadata);

        assertThat(purchase.getStatus()).isEqualTo(PurchaseStatus.ACTIVE);
        assertThat(purchase.getStartsAt()).isAfterOrEqualTo(before);
        assertThat(purchase.getExpiresAt()).isEqualTo(purchase.getStartsAt().plusDays(31));
        verify(packageActivationService).activatePurchasedPackage(purchase);
        verify(entitlementService).synchronizePeriod(purchase);
        verify(menuPublicAccessService).syncForUser(2L);
    }

    @Test
    void recordUnpaidInstallment_whenOverdue_thenKeepPaidPurchasePeriod() {
        purchase.setStatus(PurchaseStatus.ACTIVE);
        purchase.setExpiresAt(LocalDateTime.now().plusDays(20));
        when(fulfillmentRepository.findByPurchaseIdAndInstallmentId(1L, "installment-1"))
                .thenReturn(Optional.empty());

        fulfillmentService.recordUnpaidInstallment(
                purchase,
                event,
                metadata,
                FulfillmentStatus.OVERDUE
        );

        assertThat(purchase.getStatus()).isEqualTo(PurchaseStatus.ACTIVE);
        verify(purchaseRepository, never()).save(any());
        verify(menuPublicAccessService).syncForUser(2L);
    }

    @Test
    void revokeInstallment_whenNoPaidPeriodRemains_thenExpireAndRestoreFree() {
        PurchaseFulfillment existing = PurchaseFulfillment.builder()
                .purchaseId(1L)
                .installmentId("installment-1")
                .status(FulfillmentStatus.PAID)
                .eventId("paid-event")
                .build();
        when(fulfillmentRepository.findByPurchaseIdAndInstallmentId(1L, "installment-1"))
                .thenReturn(Optional.of(existing));
        when(fulfillmentRepository.findByPurchaseIdAndStatusOrderByInstallmentNumberAsc(
                1L,
                FulfillmentStatus.PAID
        )).thenReturn(List.of());

        fulfillmentService.revokeInstallment(purchase, event, metadata);

        assertThat(purchase.getStatus()).isEqualTo(PurchaseStatus.EXPIRED);
        verify(entitlementService).synchronizePeriod(purchase);
        verify(packageActivationService).ensureFreePackage(2L);
        verify(menuPublicAccessService).syncForUser(2L);
    }
}
