package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.PurchaseFulfillment;
import com.ael.algoryqrservice.model.dto.PaymentCompletedEventDto;
import com.ael.algoryqrservice.model.dto.PaymentEventMetadata;
import com.ael.algoryqrservice.model.enums.FulfillmentStatus;
import com.ael.algoryqrservice.catalog.CatalogPackages;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.repository.PlanPackageRepository;
import com.ael.algoryqrservice.repository.PurchaseFulfillmentRepository;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
