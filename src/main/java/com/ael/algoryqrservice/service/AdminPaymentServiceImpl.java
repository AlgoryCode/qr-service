package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.client.PaymentServiceClient;
import com.ael.algoryqrservice.client.dto.BillingPaymentDtos;
import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.NotFoundException;
import com.ael.algoryqrservice.exception.PaymentServiceException;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.dto.AdminPaymentDtos;
import com.ael.algoryqrservice.model.enums.PaymentStyle;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.model.enums.RefundStatus;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AdminPaymentServiceImpl implements AdminPaymentService {

    private static final int MAX_PAGE_SIZE = 50;

    private final PaymentServiceClient paymentServiceClient;
    private final PurchaseRepository purchaseRepository;
    private final PurchaseService purchaseService;

    @Override
    @Transactional(readOnly = true)
    public AdminPaymentDtos.PaymentPageResponse listPayments(
            String query,
            String status,
            String paymentType,
            String paymentStyle,
            String accountId,
            LocalDateTime createdFrom,
            LocalDateTime createdTo,
            Boolean verificationOnly,
            int page,
            int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        BillingPaymentDtos.PaymentPage paymentPage = paymentServiceClient.searchPayments(
                query,
                status,
                paymentType,
                paymentStyle,
                accountId,
                createdFrom,
                createdTo,
                verificationOnly,
                safePage,
                safeSize
        );

        List<AdminPaymentDtos.PaymentSummaryResponse> content = paymentPage.content().stream()
                .map(this::toSummary)
                .toList();

        return AdminPaymentDtos.PaymentPageResponse.builder()
                .content(content)
                .page(paymentPage.page())
                .size(paymentPage.size())
                .totalElements(paymentPage.totalElements())
                .totalPages(paymentPage.totalPages())
                .hasNext(paymentPage.hasNext())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public AdminPaymentDtos.PaymentDetailResponse getPayment(String conversationId) {
        try {
            BillingPaymentDtos.PaymentDetail payment = paymentServiceClient.getPaymentDetail(conversationId);
            return toDetail(payment);
        } catch (PaymentServiceException exception) {
            if (exception.getMessage() != null && exception.getMessage().contains("bulunamadi")) {
                throw new NotFoundException("Ödeme kaydı bulunamadı");
            }
            throw exception;
        }
    }

    @Override
    public AdminPaymentDtos.RefundResponse refundPayment(
            String conversationId,
            BigDecimal amount,
            String clientIp
    ) {
        BillingPaymentDtos.PaymentDetail payment = paymentServiceClient.getPaymentDetail(conversationId);
        if (!payment.isSuccess()) {
            throw new BadRequestException("Yalnızca başarılı ödemeler iade edilebilir");
        }
        BigDecimal remaining = payment.remainingAmount() == null ? BigDecimal.ZERO : payment.remainingAmount();
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("İade edilecek tutar bulunamadı");
        }

        BigDecimal refundAmount = amount == null ? remaining : amount;
        if (refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("İade tutarı sıfırdan büyük olmalı");
        }
        if (refundAmount.compareTo(remaining) > 0) {
            throw new BadRequestException("İade tutarı kalan tutarı aşamaz");
        }

        Optional<Purchase> linkedPurchase = findLinkedPurchase(conversationId);
        if (linkedPurchase.isPresent() && isPaidActiveSubscription(linkedPurchase.get())) {
            Purchase purchase = linkedPurchase.get();
            purchaseService.adminCancelWithRefund(purchase.getId(), refundAmount, clientIp);
            BillingPaymentDtos.PaymentDetail refreshed = paymentServiceClient.getPaymentDetail(conversationId);
            return AdminPaymentDtos.RefundResponse.builder()
                    .conversationId(conversationId)
                    .paymentTransactionId(refreshed.paymentTransactionId())
                    .refundedPrice(refundAmount)
                    .status(refreshed.status())
                    .purchaseId(purchase.getId())
                    .build();
        }

        Long userId = parseAccountId(payment.accountId())
                .or(() -> linkedPurchase.map(Purchase::getUserId))
                .orElseThrow(() -> new BadRequestException("İade için kullanıcı kimliği bulunamadı"));

        BillingPaymentDtos.RefundResult result = paymentServiceClient.refundPayment(
                userId,
                conversationId,
                refundAmount,
                clientIp
        );

        return AdminPaymentDtos.RefundResponse.builder()
                .conversationId(result.conversationId())
                .paymentTransactionId(result.paymentTransactionId())
                .refundedPrice(result.refundedPrice())
                .status(result.status())
                .purchaseId(linkedPurchase.map(Purchase::getId).orElse(null))
                .build();
    }

    private AdminPaymentDtos.PaymentSummaryResponse toSummary(BillingPaymentDtos.PaymentDetail payment) {
        Optional<Purchase> purchase = findLinkedPurchase(payment.conversationId());
        return AdminPaymentDtos.PaymentSummaryResponse.builder()
                .conversationId(payment.conversationId())
                .paymentId(payment.paymentId())
                .paymentTransactionId(payment.paymentTransactionId())
                .accountId(payment.accountId())
                .userId(resolveUserId(payment, purchase))
                .buyerEmail(payment.buyerEmail())
                .buyerName(payment.buyerName())
                .serviceName(payment.serviceName())
                .sourceReferenceId(payment.sourceReferenceId())
                .price(payment.price())
                .paidPrice(payment.paidPrice())
                .refundedAmount(payment.refundedAmount())
                .remainingAmount(payment.remainingAmount())
                .currency(payment.currency())
                .status(payment.status())
                .paymentType(payment.paymentType())
                .paymentStyle(payment.paymentStyle())
                .verificationOnly(payment.verificationOnly())
                .errorCode(payment.errorCode())
                .errorMessage(payment.errorMessage())
                .purchaseId(purchase.map(Purchase::getId).orElse(null))
                .packageName(purchase.map(Purchase::getPackageName).orElse(null))
                .refundEligible(isRefundEligible(payment, purchase))
                .createdAt(payment.createdAt())
                .updatedAt(payment.updatedAt())
                .build();
    }

    private AdminPaymentDtos.PaymentDetailResponse toDetail(BillingPaymentDtos.PaymentDetail payment) {
        Optional<Purchase> purchase = findLinkedPurchase(payment.conversationId());
        return AdminPaymentDtos.PaymentDetailResponse.builder()
                .conversationId(payment.conversationId())
                .paymentId(payment.paymentId())
                .paymentTransactionId(payment.paymentTransactionId())
                .basketId(payment.basketId())
                .accountId(payment.accountId())
                .userId(resolveUserId(payment, purchase))
                .buyerEmail(payment.buyerEmail())
                .buyerName(payment.buyerName())
                .serviceName(payment.serviceName())
                .sourceReferenceId(payment.sourceReferenceId())
                .price(payment.price())
                .paidPrice(payment.paidPrice())
                .refundedAmount(payment.refundedAmount())
                .remainingAmount(payment.remainingAmount())
                .currency(payment.currency())
                .status(payment.status())
                .paymentType(payment.paymentType())
                .paymentStyle(payment.paymentStyle())
                .bankInstallmentCount(payment.bankInstallmentCount())
                .subscriptionId(payment.subscriptionId())
                .billingCycleNumber(payment.billingCycleNumber())
                .verificationOnly(payment.verificationOnly())
                .errorCode(payment.errorCode())
                .errorMessage(payment.errorMessage())
                .purchaseId(purchase.map(Purchase::getId).orElse(null))
                .packageName(purchase.map(Purchase::getPackageName).orElse(null))
                .packageCode(purchase.map(Purchase::getPackageCode).orElse(null))
                .refundEligible(isRefundEligible(payment, purchase))
                .createdAt(payment.createdAt())
                .updatedAt(payment.updatedAt())
                .build();
    }

    private Optional<Purchase> findLinkedPurchase(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return Optional.empty();
        }
        return purchaseRepository
                .findByPaymentOrPeriodConversationId(conversationId, PageRequest.of(0, 1))
                .stream()
                .findFirst();
    }

    private Long resolveUserId(BillingPaymentDtos.PaymentDetail payment, Optional<Purchase> purchase) {
        return parseAccountId(payment.accountId())
                .or(() -> purchase.map(Purchase::getUserId))
                .orElse(null);
    }

    private boolean isRefundEligible(BillingPaymentDtos.PaymentDetail payment, Optional<Purchase> purchase) {
        if (!payment.isSuccess()) {
            return false;
        }
        BigDecimal remaining = payment.remainingAmount() == null ? BigDecimal.ZERO : payment.remainingAmount();
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        if (purchase.isEmpty()) {
            return true;
        }
        Purchase linked = purchase.get();
        if (linked.getRefundStatus() == RefundStatus.COMPLETED
                || linked.getRefundStatus() == RefundStatus.PENDING
                || linked.getRefundStatus() == RefundStatus.NEEDS_RECONCILE) {
            return false;
        }
        return true;
    }

    private boolean isPaidActiveSubscription(Purchase purchase) {
        return purchase.getStatus() == PurchaseStatus.ACTIVE
                && purchase.getPaymentStyle() == PaymentStyle.SUBSCRIPTION
                && purchase.getPurchaseType() == PurchaseType.PAID;
    }

    private Optional<Long> parseAccountId(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.valueOf(accountId.trim()));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }
}
