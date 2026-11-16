package com.ael.algoryqrservice.adminservice.application;

import com.ael.algoryqrservice.adminservice.dto.AdminPaymentDtos;
import com.ael.algoryqrservice.paymentservice.application.dto.PaymentResultDto;
import com.ael.algoryqrservice.paymentservice.application.dto.RefundResultDto;
import com.ael.algoryqrservice.paymentservice.application.service.PaymentService;
import com.ael.algoryqrservice.paymentservice.domain.exception.PaymentNotFoundException;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentSearchQuery;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentStatus;
import com.ael.algoryqrservice.paymentservice.domain.model.RefundPaymentCommand;
import com.ael.algoryqrservice.qrservice.billing.service.PurchaseService;
import com.ael.algoryqrservice.qrservice.exception.BadRequestException;
import com.ael.algoryqrservice.qrservice.exception.NotFoundException;
import com.ael.algoryqrservice.qrservice.model.Purchase;
import com.ael.algoryqrservice.qrservice.model.enums.PaymentStyle;
import com.ael.algoryqrservice.qrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.qrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.qrservice.model.enums.RefundStatus;
import com.ael.algoryqrservice.qrservice.repository.PurchaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminPaymentServiceTest {

    private PaymentService paymentService;
    private PurchaseRepository purchaseRepository;
    private PurchaseService purchaseService;
    private AdminPaymentService service;

    @BeforeEach
    void setUp() {
        paymentService = mock(PaymentService.class);
        purchaseRepository = mock(PurchaseRepository.class);
        purchaseService = mock(PurchaseService.class);
        service = new AdminPaymentService(paymentService, purchaseRepository, purchaseService);
        when(purchaseRepository.findByPaymentOrPeriodConversationIdIn(anyCollection())).thenReturn(List.of());
        when(purchaseRepository.findAllById(any())).thenReturn(List.of());
    }

    @Test
    void listLinksPurchasesInTwoBatchQueriesAndReportsRefundEligibility() {
        PaymentResultDto byConversation = payment("conv-1", "7", null, PaymentStatus.SUCCESS, "100", null);
        PaymentResultDto byReference = payment("conv-2", "8", "42", PaymentStatus.SUCCESS, "50", "50");
        PaymentResultDto unlinked = payment("conv-3", "9", null, PaymentStatus.FAILURE, "30", null);
        when(paymentService.searchPayments(any(), any()))
                .thenAnswer(inv -> new org.springframework.data.domain.PageImpl<>(
                        List.of(byConversation, byReference, unlinked), inv.getArgument(1), 3));
        when(purchaseRepository.findByPaymentOrPeriodConversationIdIn(anyCollection()))
                .thenReturn(List.of(purchase(11L, 7L, "conv-1", null, "Pro")));
        when(purchaseRepository.findAllById(any())).thenReturn(List.of(purchase(42L, 8L, "other", null, "Starter")));

        AdminPaymentDtos.PaymentPageResponse page = service.listPayments(
                " ali ", "success", null, null, null, null, null, null, 0, 500);

        assertThat(page.getContent()).hasSize(3);
        AdminPaymentDtos.PaymentSummaryResponse first = page.getContent().get(0);
        assertThat(first.getPurchaseId()).isEqualTo(11L);
        assertThat(first.getPackageName()).isEqualTo("Pro");
        assertThat(first.getUserId()).isEqualTo(7L);
        assertThat(first.getRemainingAmount()).isEqualByComparingTo("100");
        assertThat(first.isRefundEligible()).isTrue();
        AdminPaymentDtos.PaymentSummaryResponse second = page.getContent().get(1);
        assertThat(second.getPurchaseId()).isEqualTo(42L);
        assertThat(second.getRemainingAmount()).isEqualByComparingTo("0");
        assertThat(second.isRefundEligible()).isFalse();
        AdminPaymentDtos.PaymentSummaryResponse third = page.getContent().get(2);
        assertThat(third.getPurchaseId()).isNull();
        assertThat(third.isRefundEligible()).isFalse();
        assertThat(page.getSize()).isEqualTo(AdminPaymentService.MAX_PAGE_SIZE);
        assertThat(page.getTotalElements()).isEqualTo(3);

        verify(purchaseRepository, times(1)).findByPaymentOrPeriodConversationIdIn(anyCollection());
        verify(purchaseRepository, times(1)).findAllById(any());
        ArgumentCaptor<PaymentSearchQuery> query = ArgumentCaptor.forClass(PaymentSearchQuery.class);
        verify(paymentService).searchPayments(query.capture(), any());
        assertThat(query.getValue().query()).isEqualTo("ali");
        assertThat(query.getValue().status()).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    void aSourceReferenceOfAnotherAccountIsNotLinked() {
        PaymentResultDto payment = payment("conv-1", "8", "42", PaymentStatus.SUCCESS, "50", null);
        when(paymentService.retrievePayment("conv-1")).thenReturn(payment);
        when(purchaseRepository.findAllById(any())).thenReturn(List.of(purchase(42L, 99L, "x", null, "Pro")));

        AdminPaymentDtos.PaymentDetailResponse detail = service.getPayment("conv-1");

        assertThat(detail.getPurchaseId()).isNull();
        assertThat(detail.getUserId()).isEqualTo(8L);
    }

    @Test
    void anUnknownFilterValueIsABadRequest() {
        assertThatThrownBy(() -> service.listPayments(null, "PAID", null, null, null, null, null, null, 0, 20))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void aMissingPaymentIsNotFound() {
        when(paymentService.retrievePayment("nope")).thenThrow(new PaymentNotFoundException("nope"));

        assertThatThrownBy(() -> service.getPayment("nope")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void refundingTheCurrentChargeOfAnActiveSubscriptionGoesThroughThePurchase() {
        PaymentResultDto payment = payment("conv-1", "7", "11", PaymentStatus.SUCCESS, "100", null);
        PaymentResultDto refunded = payment("conv-1", "7", "11", PaymentStatus.REFUNDED, "100", "100");
        refunded.setPaymentTransactionId("tx-1");
        when(paymentService.retrievePayment("conv-1")).thenReturn(payment, refunded);
        Purchase purchase = purchase(11L, 7L, "conv-0", "conv-1", "Pro");
        purchase.setStatus(PurchaseStatus.ACTIVE);
        purchase.setPaymentStyle(PaymentStyle.SUBSCRIPTION);
        purchase.setPurchaseType(PurchaseType.PAID);
        when(purchaseRepository.findByPaymentOrPeriodConversationIdIn(anyCollection())).thenReturn(List.of(purchase));

        AdminPaymentDtos.RefundResponse response = service.refundPayment("conv-1", new BigDecimal("40"), "1.2.3.4");

        verify(purchaseService).adminCancelWithRefund(11L, new BigDecimal("40"), "1.2.3.4");
        verify(paymentService, never()).refundPayment(any());
        assertThat(response.getPurchaseId()).isEqualTo(11L);
        assertThat(response.getStatus()).isEqualTo("REFUNDED");
        assertThat(response.getPaymentTransactionId()).isEqualTo("tx-1");
        assertThat(response.getRefundedPrice()).isEqualByComparingTo("40");
    }

    @Test
    void anOldPeriodChargeIsRefundedDirectly() {
        PaymentResultDto payment = payment("conv-0", "7", "11", PaymentStatus.SUCCESS, "100", null);
        when(paymentService.retrievePayment("conv-0")).thenReturn(payment);
        Purchase purchase = purchase(11L, 7L, "conv-0", "conv-1", "Pro");
        purchase.setStatus(PurchaseStatus.ACTIVE);
        purchase.setPaymentStyle(PaymentStyle.SUBSCRIPTION);
        purchase.setPurchaseType(PurchaseType.PAID);
        when(purchaseRepository.findByPaymentOrPeriodConversationIdIn(anyCollection())).thenReturn(List.of(purchase));
        when(paymentService.refundPayment(any())).thenReturn(
                RefundResultDto.builder().conversationId("conv-0").refundedPrice(new BigDecimal("100")).status("success").build());

        AdminPaymentDtos.RefundResponse response = service.refundPayment("conv-0", null, null);

        verify(purchaseService, never()).adminCancelWithRefund(any(), any(), anyString());
        assertThat(response.getPurchaseId()).isEqualTo(11L);
    }

    @Test
    void anUnlinkedPaymentIsRefundedDirectlyForTheWholeRemainingAmount() {
        PaymentResultDto payment = payment("conv-1", "7", null, PaymentStatus.SUCCESS, "100", "30");
        when(paymentService.retrievePayment("conv-1")).thenReturn(payment);
        when(paymentService.refundPayment(any())).thenReturn(RefundResultDto.builder()
                .conversationId("conv-1").paymentTransactionId("tx").refundedPrice(new BigDecimal("70"))
                .status("success").build());

        AdminPaymentDtos.RefundResponse response = service.refundPayment("conv-1", null, null);

        ArgumentCaptor<RefundPaymentCommand> command = ArgumentCaptor.forClass(RefundPaymentCommand.class);
        verify(paymentService).refundPayment(command.capture());
        assertThat(command.getValue().getPrice()).isEqualByComparingTo("70");
        assertThat(command.getValue().getIp()).isEqualTo("127.0.0.1");
        assertThat(response.getPurchaseId()).isNull();
        assertThat(response.getRefundedPrice()).isEqualByComparingTo("70");
    }

    @Test
    void refundsAreRejectedWhenNotSuccessfulOverTheRemainingOrAlreadyInFlight() {
        when(paymentService.retrievePayment("failed"))
                .thenReturn(payment("failed", "7", null, PaymentStatus.FAILURE, "100", null));
        assertThatThrownBy(() -> service.refundPayment("failed", null, null)).isInstanceOf(BadRequestException.class);

        when(paymentService.retrievePayment("ok"))
                .thenReturn(payment("ok", "7", null, PaymentStatus.SUCCESS, "100", "90"));
        assertThatThrownBy(() -> service.refundPayment("ok", new BigDecimal("11"), null))
                .isInstanceOf(BadRequestException.class);

        when(paymentService.retrievePayment("pending"))
                .thenReturn(payment("pending", "7", null, PaymentStatus.SUCCESS, "100", null));
        Purchase purchase = purchase(11L, 7L, "pending", null, "Pro");
        purchase.setRefundStatus(RefundStatus.PENDING);
        when(purchaseRepository.findByPaymentOrPeriodConversationIdIn(anyCollection())).thenReturn(List.of(purchase));
        assertThatThrownBy(() -> service.refundPayment("pending", null, null))
                .isInstanceOf(BadRequestException.class);
        assertThat(service.getPayment("pending").isRefundEligible()).isFalse();

        verify(paymentService, never()).refundPayment(any());
        verify(purchaseService, never()).adminCancelWithRefund(any(), any(), anyString());
    }

    private static PaymentResultDto payment(
            String conversationId,
            String accountId,
            String sourceReferenceId,
            PaymentStatus status,
            String paid,
            String refunded
    ) {
        return PaymentResultDto.builder()
                .conversationId(conversationId)
                .accountId(accountId)
                .sourceReferenceId(sourceReferenceId)
                .status(status)
                .price(new BigDecimal(paid))
                .paidPrice(new BigDecimal(paid))
                .refundedAmount(refunded == null ? null : new BigDecimal(refunded))
                .currency("TRY")
                .build();
    }

    private static Purchase purchase(Long id, Long merchantId, String paymentConversationId,
                                     String currentPeriodConversationId, String packageName) {
        Purchase purchase = new Purchase();
        purchase.setId(id);
        purchase.setMerchantId(merchantId);
        purchase.setPaymentConversationId(paymentConversationId);
        purchase.setCurrentPeriodConversationId(currentPeriodConversationId);
        purchase.setPackageName(packageName);
        purchase.setPackageCode("PRO_PACKAGE");
        purchase.setStatus(PurchaseStatus.EXPIRED);
        purchase.setRefundStatus(RefundStatus.NONE);
        return purchase;
    }
}
