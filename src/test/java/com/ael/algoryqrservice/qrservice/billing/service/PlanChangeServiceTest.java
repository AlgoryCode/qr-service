package com.ael.algoryqrservice.qrservice.billing.service;

import com.ael.algoryqrservice.fulfillmentservice.application.service.FulfillmentService;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.ActivePackage;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.PackageStatus;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.SubscriptionKind;
import com.ael.algoryqrservice.qrservice.billing.dto.PaymentCompletedEventDto;
import com.ael.algoryqrservice.qrservice.billing.dto.PaymentEventMetadata;
import com.ael.algoryqrservice.qrservice.billing.dto.PlanChangePreviewResponse;
import com.ael.algoryqrservice.qrservice.billing.dto.PlanChangeRequest;
import com.ael.algoryqrservice.qrservice.billing.dto.PlanChangeResponse;
import com.ael.algoryqrservice.qrservice.billing.paymentrequestmapper.service.PaymentRequestMapper;
import com.ael.algoryqrservice.qrservice.client.PaymentServiceClient;
import com.ael.algoryqrservice.qrservice.config.AppProperties;
import com.ael.algoryqrservice.qrservice.config.PaymentClientProperties;
import com.ael.algoryqrservice.qrservice.exception.BadRequestException;
import com.ael.algoryqrservice.qrservice.exception.NotFoundException;
import com.ael.algoryqrservice.qrservice.model.PlanChange;
import com.ael.algoryqrservice.qrservice.model.PlanPackage;
import com.ael.algoryqrservice.qrservice.model.PlanPackageItem;
import com.ael.algoryqrservice.qrservice.model.Purchase;
import com.ael.algoryqrservice.qrservice.model.User;
import com.ael.algoryqrservice.qrservice.model.enums.BillingPeriod;
import com.ael.algoryqrservice.qrservice.model.enums.PaymentStyle;
import com.ael.algoryqrservice.qrservice.model.enums.PlanChangeDirection;
import com.ael.algoryqrservice.qrservice.model.enums.PlanChangeStatus;
import com.ael.algoryqrservice.qrservice.model.enums.PlanChangeTiming;
import com.ael.algoryqrservice.qrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.qrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.qrservice.model.enums.SubscriptionStatus;
import com.ael.algoryqrservice.qrservice.repository.PlanChangeRepository;
import com.ael.algoryqrservice.qrservice.repository.PlanPackageRepository;
import com.ael.algoryqrservice.qrservice.repository.PurchaseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanChangeServiceTest {

    private static final long MERCHANT_ID = 7L;
    private static final long PURCHASE_ID = 100L;
    private static final String SUBSCRIPTION_ID = "sub-1";

    @Mock private PlanChangeRepository planChangeRepository;
    @Mock private PurchaseRepository purchaseRepository;
    @Mock private PlanPackageRepository planPackageRepository;
    @Mock private FulfillmentService fulfillmentService;
    @Mock private PaymentServiceClient paymentServiceClient;
    @Mock private PaymentRequestMapper paymentRequestMapper;
    @Mock private PurchaseFulfillmentService purchaseFulfillmentService;
    @Mock private PurchaseLogService purchaseLogService;
    @Mock private AppProperties appProperties;
    @Mock private PaymentClientProperties paymentClientProperties;
    @Mock private RecurringAddonBilling recurringAddonBilling;

    @InjectMocks
    private PlanChangeService service;

    private final PlanPackage basic = planPackage(1L, "BASIC", "100.00");
    private final PlanPackage pro = planPackage(2L, "PRO", "250.00");

    @Test
    void proratedCharge_chargesTheDifferenceForTheRemainingDays() {
        LocalDateTime start = LocalDateTime.of(2026, 11, 1, 0, 0);
        LocalDateTime end = start.plusDays(30);

        assertThat(PlanChangeService.proratedCharge(
                new BigDecimal("100.00"), new BigDecimal("250.00"), start, end, end.minusDays(10)))
                .isEqualByComparingTo("50.00");
        // 99.99 x 7 / 31 = 22.5783... -> 22.58 (HALF_UP)
        assertThat(PlanChangeService.proratedCharge(
                new BigDecimal("100.00"), new BigDecimal("199.99"), start, start.plusDays(31), start.plusDays(24)))
                .isEqualTo(new BigDecimal("22.58"));
        assertThat(PlanChangeService.proratedCharge(
                new BigDecimal("100.00"), new BigDecimal("250.00"), start, end, start))
                .isEqualByComparingTo("150.00");
    }

    @Test
    void proratedCharge_isZeroForDowngradesAndAfterPeriodEnd() {
        LocalDateTime start = LocalDateTime.of(2026, 11, 1, 0, 0);
        LocalDateTime end = start.plusDays(30);

        assertThat(PlanChangeService.proratedCharge(
                new BigDecimal("250.00"), new BigDecimal("100.00"), start, end, start.plusDays(5)))
                .isEqualByComparingTo("0");
        assertThat(PlanChangeService.proratedCharge(
                new BigDecimal("100.00"), new BigDecimal("250.00"), start, end, end.plusDays(2)))
                .isEqualByComparingTo("0");
    }

    @Test
    void preview_upgradeOffersProratedImmediateAndNextPeriod() {
        Purchase current = currentPurchase(basic, LocalDateTime.now().plusDays(15).plusHours(1));
        stubCurrent(current, basic, false);
        when(planPackageRepository.findByIdWithItems(2L)).thenReturn(Optional.of(pro));

        PlanChangePreviewResponse preview = service.preview(MERCHANT_ID, 2L);

        assertThat(preview.getDirection()).isEqualTo(PlanChangeDirection.UPGRADE);
        assertThat(preview.getOptions()).extracting(option -> option.getTiming())
                .containsExactly(PlanChangeTiming.IMMEDIATE, PlanChangeTiming.NEXT_PERIOD);
        // One month back from the billing date; 15 of those days remain.
        long periodDays = java.time.temporal.ChronoUnit.DAYS.between(
                current.getExpiresAt().minusMonths(1), current.getExpiresAt());
        BigDecimal expected = new BigDecimal("150.00").multiply(BigDecimal.valueOf(15))
                .divide(BigDecimal.valueOf(periodDays), 2, java.math.RoundingMode.HALF_UP);
        assertThat(preview.getOptions().get(0).getChargeNow()).isEqualByComparingTo(expected);
        assertThat(preview.getOptions().get(1).getEffectiveAt()).isEqualTo(current.getExpiresAt());
    }

    @Test
    void request_downgradeIsScheduledForPeriodEndWithoutCharge() {
        Purchase current = currentPurchase(pro, LocalDateTime.now().plusDays(10));
        stubCurrent(current, pro, true);
        when(planPackageRepository.findByIdWithItems(1L)).thenReturn(Optional.of(basic));
        when(planChangeRepository.save(any(PlanChange.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PlanChangeResponse response = service.request(user(), request(1L, PlanChangeTiming.NEXT_PERIOD), "127.0.0.1");

        ArgumentCaptor<PlanChange> saved = ArgumentCaptor.forClass(PlanChange.class);
        verify(planChangeRepository).save(saved.capture());
        PlanChange change = saved.getValue();
        assertThat(change.getStatus()).isEqualTo(PlanChangeStatus.SCHEDULED);
        assertThat(change.getDirection()).isEqualTo(PlanChangeDirection.DOWNGRADE);
        assertThat(change.getTiming()).isEqualTo(PlanChangeTiming.NEXT_PERIOD);
        assertThat(change.getEffectiveAt()).isEqualTo(current.getExpiresAt());
        assertThat(change.getChargeAmount()).isEqualByComparingTo("100.00");
        assertThat(response.getStatus()).isEqualTo(PlanChangeStatus.SCHEDULED);
        verify(paymentServiceClient, never()).initializeCheckoutForm(any(), any());
    }

    @Test
    void request_immediateDowngradeIsRejected() {
        Purchase current = currentPurchase(pro, LocalDateTime.now().plusDays(10));
        stubCurrent(current, pro, true);
        when(planPackageRepository.findByIdWithItems(1L)).thenReturn(Optional.of(basic));

        assertThatThrownBy(() -> service.request(user(), request(1L, PlanChangeTiming.IMMEDIATE), "127.0.0.1"))
                .isInstanceOf(BadRequestException.class);
        verify(planChangeRepository, never()).save(any());
    }

    @Test
    void request_rejectedWhileAnotherChangeIsScheduled() {
        Purchase current = currentPurchase(pro, LocalDateTime.now().plusDays(10));
        stubCurrent(current, pro, true);
        when(planPackageRepository.findByIdWithItems(1L)).thenReturn(Optional.of(basic));
        when(planChangeRepository.existsByMerchantIdAndStatus(MERCHANT_ID, PlanChangeStatus.SCHEDULED)).thenReturn(true);

        assertThatThrownBy(() -> service.request(user(), request(1L, PlanChangeTiming.NEXT_PERIOD), "127.0.0.1"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void cancel_scheduledChangeBecomesCancelled() {
        PlanChange change = scheduledChange(MERCHANT_ID);
        when(planChangeRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(change));

        PlanChangeResponse response = service.cancel(MERCHANT_ID, 5L);

        assertThat(change.getStatus()).isEqualTo(PlanChangeStatus.CANCELLED);
        assertThat(change.getCompletedAt()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(PlanChangeStatus.CANCELLED);
        verify(planChangeRepository).save(change);
    }

    @Test
    void cancel_rejectsOtherMerchantsAndFinishedChanges() {
        when(planChangeRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(scheduledChange(99L)));
        assertThatThrownBy(() -> service.cancel(MERCHANT_ID, 5L)).isInstanceOf(NotFoundException.class);

        PlanChange completed = scheduledChange(MERCHANT_ID);
        completed.setStatus(PlanChangeStatus.COMPLETED);
        when(planChangeRepository.findByIdForUpdate(6L)).thenReturn(Optional.of(completed));
        assertThatThrownBy(() -> service.cancel(MERCHANT_ID, 6L)).isInstanceOf(BadRequestException.class);
        verify(planChangeRepository, never()).save(any());
    }

    @Test
    void executeScheduled_switchesPurchaseAndRenewalToTheTargetPackage() {
        Purchase current = currentPurchase(pro, LocalDateTime.now().plusMinutes(30));
        PlanChange change = scheduledChange(MERCHANT_ID);
        when(planChangeRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(change));
        when(purchaseRepository.findByIdForUpdate(PURCHASE_ID)).thenReturn(Optional.of(current));
        when(planPackageRepository.findByIdWithItems(1L)).thenReturn(Optional.of(basic));
        when(paymentServiceClient.switchSubscriptionPlan(
                eq(MERCHANT_ID), eq(SUBSCRIPTION_ID), eq(PURCHASE_ID), any(), anyMap())).thenReturn(true);

        service.executeScheduled(5L);

        ArgumentCaptor<BigDecimal> amount = ArgumentCaptor.forClass(BigDecimal.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(paymentServiceClient).switchSubscriptionPlan(
                eq(MERCHANT_ID), eq(SUBSCRIPTION_ID), eq(PURCHASE_ID), amount.capture(), metadata.capture());
        assertThat(amount.getValue()).isEqualByComparingTo("100.00");
        assertThat(metadata.getValue()).containsEntry("packageCode", "BASIC").containsEntry("purchaseId", PURCHASE_ID);
        assertThat(current.getPackageCode()).isEqualTo("BASIC");
        assertThat(current.getPrice()).isEqualByComparingTo("100.00");
        assertThat(current.getRecurringPrice()).isEqualByComparingTo("100.00");
        assertThat(change.getStatus()).isEqualTo(PlanChangeStatus.COMPLETED);
    }

    @Test
    void executeScheduled_failsWhenTheSubscriptionNoLongerRenews() {
        Purchase current = currentPurchase(pro, LocalDateTime.now().plusMinutes(30));
        current.setCancelAtPeriodEnd(true);
        PlanChange change = scheduledChange(MERCHANT_ID);
        when(planChangeRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(change));
        when(purchaseRepository.findByIdForUpdate(PURCHASE_ID)).thenReturn(Optional.of(current));

        service.executeScheduled(5L);

        assertThat(change.getStatus()).isEqualTo(PlanChangeStatus.FAILED);
        assertThat(current.getPackageCode()).isEqualTo("PRO");
        verify(paymentServiceClient, never()).switchSubscriptionPlan(any(), any(), any(), any(), any());
    }

    @Test
    void fulfillUpgradePayment_keepsBillingDateAndMovesTheSubscription() {
        LocalDateTime billingDate = LocalDateTime.now().plusDays(12);
        Purchase from = currentPurchase(basic, billingDate);
        Purchase upgrade = Purchase.builder()
                .id(200L)
                .merchantId(MERCHANT_ID)
                .packageId(2L)
                .packageCode("PRO")
                .price(new BigDecimal("250.00"))
                .recurringPrice(new BigDecimal("250.00"))
                .paymentStyle(PaymentStyle.SUBSCRIPTION)
                .billingPeriod(BillingPeriod.MONTHLY)
                .paymentConversationId("qr-new")
                .status(PurchaseStatus.PENDING)
                .build();
        PlanChange change = PlanChange.builder()
                .id(8L)
                .merchantId(MERCHANT_ID)
                .fromPurchaseId(PURCHASE_ID)
                .fromPackageId(1L)
                .toPackageId(2L)
                .timing(PlanChangeTiming.IMMEDIATE)
                .status(PlanChangeStatus.PENDING_PAYMENT)
                .resultingPurchaseId(200L)
                .build();
        PaymentCompletedEventDto event = new PaymentCompletedEventDto();
        event.setPaymentId("pay-1");
        event.setAmount(new BigDecimal("72.58"));
        PaymentEventMetadata metadata = new PaymentEventMetadata(200L, MERCHANT_ID, 2L, "PRO", "qr-new",
                "i-1", 1, 1, LocalDateTime.now(), LocalDateTime.now().plusDays(30));
        when(planChangeRepository.findByResultingPurchaseIdForUpdate(200L)).thenReturn(Optional.of(change));
        when(purchaseRepository.findByIdForUpdate(PURCHASE_ID)).thenReturn(Optional.of(from));
        when(planPackageRepository.findById(2L)).thenReturn(Optional.of(pro));
        when(paymentServiceClient.switchSubscriptionPlan(
                eq(MERCHANT_ID), eq(SUBSCRIPTION_ID), eq(200L), any(), anyMap())).thenReturn(true);

        assertThat(service.fulfillUpgradePayment(upgrade, event, metadata)).isTrue();

        verify(purchaseFulfillmentService).fulfillPlanChangeInstallment(upgrade, event, metadata, billingDate);
        verify(paymentServiceClient).switchSubscriptionPlan(
                eq(MERCHANT_ID), eq(SUBSCRIPTION_ID), eq(200L), eq(new BigDecimal("250.00")), anyMap());
        assertThat(upgrade.getSubscriptionId()).isEqualTo(SUBSCRIPTION_ID);
        assertThat(from.getStatus()).isEqualTo(PurchaseStatus.SUPERSEDED);
        assertThat(change.getStatus()).isEqualTo(PlanChangeStatus.COMPLETED);
    }

    @Test
    void fulfillUpgradePayment_ignoresOrdinaryPurchases() {
        Purchase purchase = Purchase.builder().id(300L).build();
        when(planChangeRepository.findByResultingPurchaseIdForUpdate(300L)).thenReturn(Optional.empty());

        assertThat(service.fulfillUpgradePayment(purchase, new PaymentCompletedEventDto(), null)).isFalse();
        verify(purchaseFulfillmentService, never()).fulfillPlanChangeInstallment(any(), any(), any(), any());
    }

    private void stubCurrent(Purchase current, PlanPackage currentPackage, boolean forUpdate) {
        when(fulfillmentService.findActivePackage(MERCHANT_ID)).thenReturn(Optional.of(new ActivePackage(
                1L, MERCHANT_ID, 11L, current.getId(), currentPackage.getId(), currentPackage.getCode(),
                LocalDate.now().minusDays(10), current.getExpiresAt().toLocalDate(), PackageStatus.ACTIVE,
                Instant.now(), Instant.now(), SubscriptionKind.PAID, null)));
        if (forUpdate) {
            when(purchaseRepository.findByIdForUpdate(current.getId())).thenReturn(Optional.of(current));
        } else {
            when(purchaseRepository.findById(current.getId())).thenReturn(Optional.of(current));
        }
        when(planPackageRepository.findById(currentPackage.getId())).thenReturn(Optional.of(currentPackage));
    }

    private static Purchase currentPurchase(PlanPackage planPackage, LocalDateTime expiresAt) {
        return Purchase.builder()
                .id(PURCHASE_ID)
                .merchantId(MERCHANT_ID)
                .packageId(planPackage.getId())
                .packageCode(planPackage.getCode())
                .packageName(planPackage.getName())
                .price(planPackage.getPrice())
                .recurringPrice(planPackage.getPrice())
                .currency("TRY")
                .paymentStyle(PaymentStyle.SUBSCRIPTION)
                .billingPeriod(BillingPeriod.MONTHLY)
                .billingIntervalMonths(1)
                .purchaseType(PurchaseType.PAID)
                .status(PurchaseStatus.ACTIVE)
                .subscriptionId(SUBSCRIPTION_ID)
                .subscriptionStatus(SubscriptionStatus.ACTIVE)
                .paymentConversationId("qr-old")
                .startsAt(expiresAt.minusMonths(3))
                .expiresAt(expiresAt)
                .build();
    }

    private static PlanPackage planPackage(Long id, String code, String price) {
        PlanPackage planPackage = PlanPackage.builder()
                .id(id)
                .code(code)
                .name(code)
                .price(new BigDecimal(price))
                .currency("TRY")
                .active(true)
                .validityDays(30)
                .build();
        planPackage.setItems(List.of(new PlanPackageItem()));
        return planPackage;
    }

    private static PlanChange scheduledChange(Long merchantId) {
        return PlanChange.builder()
                .id(5L)
                .merchantId(merchantId)
                .fromPurchaseId(PURCHASE_ID)
                .fromPackageId(2L)
                .toPackageId(1L)
                .direction(PlanChangeDirection.DOWNGRADE)
                .timing(PlanChangeTiming.NEXT_PERIOD)
                .status(PlanChangeStatus.SCHEDULED)
                .chargeAmount(new BigDecimal("100.00"))
                .effectiveAt(LocalDateTime.now().plusMinutes(30))
                .build();
    }

    private static User user() {
        User user = new User();
        user.setId(MERCHANT_ID);
        return user;
    }

    private static PlanChangeRequest request(Long toPackageId, PlanChangeTiming timing) {
        PlanChangeRequest request = new PlanChangeRequest();
        request.setToPackageId(toPackageId);
        request.setTiming(timing);
        request.setWarningAck(true);
        return request;
    }
}
