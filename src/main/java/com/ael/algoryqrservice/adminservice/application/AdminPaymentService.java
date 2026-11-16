package com.ael.algoryqrservice.adminservice.application;

import com.ael.algoryqrservice.adminservice.dto.AdminPaymentDtos;
import com.ael.algoryqrservice.paymentservice.application.dto.PaymentResultDto;
import com.ael.algoryqrservice.paymentservice.application.dto.RefundResultDto;
import com.ael.algoryqrservice.paymentservice.application.service.PaymentService;
import com.ael.algoryqrservice.paymentservice.domain.exception.DomainException;
import com.ael.algoryqrservice.paymentservice.domain.exception.PaymentNotFoundException;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentSearchQuery;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentStatus;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentStyle;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentType;
import com.ael.algoryqrservice.paymentservice.domain.model.RefundPaymentCommand;
import com.ael.algoryqrservice.qrservice.billing.service.PurchaseService;
import com.ael.algoryqrservice.qrservice.exception.BadRequestException;
import com.ael.algoryqrservice.qrservice.exception.NotFoundException;
import com.ael.algoryqrservice.qrservice.model.Purchase;
import com.ael.algoryqrservice.qrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.qrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.qrservice.model.enums.RefundStatus;
import com.ael.algoryqrservice.qrservice.repository.PurchaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Admin view of the in-process payment module, enriched with the qrservice purchase each payment
 * paid for. A payment is linked to a purchase through its conversation id (the purchase's first
 * or current-period payment) or, failing that, its {@code sourceReferenceId} (the purchase id the
 * checkout was opened for).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminPaymentService {

    static final int MAX_PAGE_SIZE = 50;
    private static final String DEFAULT_CLIENT_IP = "127.0.0.1";

    private final PaymentService paymentService;
    private final PurchaseRepository purchaseRepository;
    private final PurchaseService purchaseService;

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
        PaymentSearchQuery searchQuery = PaymentSearchQuery.builder()
                .query(blankToNull(query))
                .status(parseEnum(PaymentStatus.class, status, "status"))
                .paymentType(parseEnum(PaymentType.class, paymentType, "paymentType"))
                .paymentStyle(parseEnum(PaymentStyle.class, paymentStyle, "paymentStyle"))
                .accountId(blankToNull(accountId))
                .createdFrom(createdFrom)
                .createdTo(createdTo)
                .verificationOnly(verificationOnly)
                .build();
        Page<PaymentResultDto> payments = paymentService.searchPayments(
                searchQuery,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        PurchaseLinks links = loadLinks(payments.getContent());
        List<AdminPaymentDtos.PaymentSummaryResponse> content = payments.getContent().stream()
                .map(payment -> toSummary(payment, links.find(payment)))
                .toList();

        return AdminPaymentDtos.PaymentPageResponse.builder()
                .content(content)
                .page(payments.getNumber())
                .size(payments.getSize())
                .totalElements(payments.getTotalElements())
                .totalPages(payments.getTotalPages())
                .hasNext(payments.hasNext())
                .build();
    }

    @Transactional(readOnly = true)
    public AdminPaymentDtos.PaymentDetailResponse getPayment(String conversationId) {
        PaymentResultDto payment = requirePayment(conversationId);
        return toDetail(payment, loadLinks(List.of(payment)).find(payment));
    }

    /**
     * Refunds {@code amount} (the whole remaining amount when null). A payment that is the current
     * charge of an active paid subscription goes through the purchase refund path, which also
     * cancels the subscription and its package. Any other payment is refunded directly; a full
     * refund still reaches the linked purchase through the payment module's REFUNDED event.
     */
    public AdminPaymentDtos.RefundResponse refundPayment(String conversationId, BigDecimal amount, String clientIp) {
        PaymentResultDto payment = requirePayment(conversationId);
        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new BadRequestException("Yalnızca başarılı ödemeler iade edilebilir");
        }
        BigDecimal remaining = remainingAmount(payment);
        if (remaining.signum() <= 0) {
            throw new BadRequestException("İade edilecek tutar bulunamadı");
        }
        BigDecimal refundAmount = amount == null ? remaining : amount;
        if (refundAmount.signum() <= 0) {
            throw new BadRequestException("İade tutarı sıfırdan büyük olmalı");
        }
        if (refundAmount.compareTo(remaining) > 0) {
            throw new BadRequestException("İade tutarı kalan tutarı aşamaz");
        }

        Optional<Purchase> linked = loadLinks(List.of(payment)).find(payment);
        if (linked.isPresent() && refundInFlight(linked.get())) {
            throw new BadRequestException("Bu satın alım için iade zaten işleniyor");
        }
        String ip = clientIp == null || clientIp.isBlank() ? DEFAULT_CLIENT_IP : clientIp;

        if (linked.isPresent() && isCurrentChargeOfActiveSubscription(linked.get(), conversationId)) {
            Purchase purchase = linked.get();
            try {
                purchaseService.adminCancelWithRefund(purchase.getId(), refundAmount, ip);
            } catch (PaymentNotFoundException exception) {
                throw new NotFoundException("Ödeme kaydı bulunamadı");
            } catch (DomainException exception) {
                throw new BadRequestException(exception.getMessage());
            }
            PaymentResultDto refreshed = paymentService.retrievePayment(conversationId);
            return AdminPaymentDtos.RefundResponse.builder()
                    .conversationId(conversationId)
                    .paymentTransactionId(refreshed.getPaymentTransactionId())
                    .refundedPrice(refundAmount)
                    .status(name(refreshed.getStatus()))
                    .purchaseId(purchase.getId())
                    .build();
        }

        RefundResultDto result;
        try {
            result = paymentService.refundPayment(RefundPaymentCommand.builder()
                    .conversationId(conversationId)
                    .locale("tr")
                    .price(refundAmount)
                    .ip(ip)
                    .build());
        } catch (PaymentNotFoundException exception) {
            throw new NotFoundException("Ödeme kaydı bulunamadı");
        } catch (DomainException exception) {
            throw new BadRequestException(exception.getMessage());
        }
        return AdminPaymentDtos.RefundResponse.builder()
                .conversationId(result.getConversationId() == null ? conversationId : result.getConversationId())
                .paymentTransactionId(result.getPaymentTransactionId())
                .refundedPrice(result.getRefundedPrice() == null ? refundAmount : result.getRefundedPrice())
                .status(result.getStatus())
                .purchaseId(linked.map(Purchase::getId).orElse(null))
                .build();
    }

    private PaymentResultDto requirePayment(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            throw new NotFoundException("Ödeme kaydı bulunamadı");
        }
        try {
            PaymentResultDto payment = paymentService.retrievePayment(conversationId);
            if (payment == null) {
                throw new NotFoundException("Ödeme kaydı bulunamadı");
            }
            return payment;
        } catch (PaymentNotFoundException exception) {
            throw new NotFoundException("Ödeme kaydı bulunamadı");
        }
    }

    /** Two queries per page at most: purchases by conversation id, then by referenced purchase id. */
    private PurchaseLinks loadLinks(List<PaymentResultDto> payments) {
        Set<String> conversationIds = new LinkedHashSet<>();
        Set<Long> referencedIds = new LinkedHashSet<>();
        for (PaymentResultDto payment : payments) {
            if (payment.getConversationId() != null && !payment.getConversationId().isBlank()) {
                conversationIds.add(payment.getConversationId());
            }
            parseLong(payment.getSourceReferenceId()).ifPresent(referencedIds::add);
        }

        Map<String, Purchase> byConversation = new HashMap<>();
        Map<Long, Purchase> byId = new HashMap<>();
        if (!conversationIds.isEmpty()) {
            // Newest purchase first, so putIfAbsent keeps the most recent one per conversation.
            for (Purchase purchase : purchaseRepository.findByPaymentOrPeriodConversationIdIn(conversationIds)) {
                byId.putIfAbsent(purchase.getId(), purchase);
                putIfMatching(byConversation, purchase.getPaymentConversationId(), conversationIds, purchase);
                putIfMatching(byConversation, purchase.getCurrentPeriodConversationId(), conversationIds, purchase);
            }
        }
        Set<Long> missing = new LinkedHashSet<>(referencedIds);
        missing.removeAll(byId.keySet());
        if (!missing.isEmpty()) {
            purchaseRepository.findAllById(missing).forEach(purchase -> byId.putIfAbsent(purchase.getId(), purchase));
        }
        return new PurchaseLinks(byConversation, byId);
    }

    private static void putIfMatching(
            Map<String, Purchase> target,
            String conversationId,
            Set<String> wanted,
            Purchase purchase
    ) {
        if (conversationId != null && wanted.contains(conversationId)) {
            target.putIfAbsent(conversationId, purchase);
        }
    }

    private record PurchaseLinks(Map<String, Purchase> byConversation, Map<Long, Purchase> byId) {

        Optional<Purchase> find(PaymentResultDto payment) {
            Purchase byConversationId = payment.getConversationId() == null
                    ? null
                    : byConversation.get(payment.getConversationId());
            if (byConversationId != null) {
                return Optional.of(byConversationId);
            }
            Optional<Long> accountId = parseLong(payment.getAccountId());
            return parseLong(payment.getSourceReferenceId())
                    .map(byId::get)
                    // A reference id is only trusted when it belongs to the paying account.
                    .filter(purchase -> accountId.isEmpty()
                            || Objects.equals(accountId.get(), purchase.getMerchantId()));
        }
    }

    private AdminPaymentDtos.PaymentSummaryResponse toSummary(PaymentResultDto payment, Optional<Purchase> purchase) {
        return AdminPaymentDtos.PaymentSummaryResponse.builder()
                .conversationId(payment.getConversationId())
                .paymentId(payment.getPaymentId())
                .paymentTransactionId(payment.getPaymentTransactionId())
                .accountId(payment.getAccountId())
                .userId(resolveUserId(payment, purchase))
                .buyerEmail(payment.getBuyerEmail())
                .buyerName(payment.getBuyerName())
                .serviceName(payment.getServiceName())
                .sourceReferenceId(payment.getSourceReferenceId())
                .price(payment.getPrice())
                .paidPrice(payment.getPaidPrice())
                .refundedAmount(payment.getRefundedAmount())
                .remainingAmount(remainingAmount(payment))
                .currency(payment.getCurrency())
                .status(name(payment.getStatus()))
                .paymentType(name(payment.getPaymentType()))
                .paymentStyle(name(payment.getPaymentStyle()))
                .verificationOnly(payment.isVerificationOnly())
                .errorCode(payment.getErrorCode())
                .errorMessage(payment.getErrorMessage())
                .purchaseId(purchase.map(Purchase::getId).orElse(null))
                .packageName(purchase.map(Purchase::getPackageName).orElse(null))
                .refundEligible(isRefundEligible(payment, purchase))
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .build();
    }

    private AdminPaymentDtos.PaymentDetailResponse toDetail(PaymentResultDto payment, Optional<Purchase> purchase) {
        return AdminPaymentDtos.PaymentDetailResponse.builder()
                .conversationId(payment.getConversationId())
                .paymentId(payment.getPaymentId())
                .paymentTransactionId(payment.getPaymentTransactionId())
                .basketId(payment.getBasketId())
                .accountId(payment.getAccountId())
                .userId(resolveUserId(payment, purchase))
                .buyerEmail(payment.getBuyerEmail())
                .buyerName(payment.getBuyerName())
                .serviceName(payment.getServiceName())
                .sourceReferenceId(payment.getSourceReferenceId())
                .price(payment.getPrice())
                .paidPrice(payment.getPaidPrice())
                .refundedAmount(payment.getRefundedAmount())
                .remainingAmount(remainingAmount(payment))
                .currency(payment.getCurrency())
                .status(name(payment.getStatus()))
                .paymentType(name(payment.getPaymentType()))
                .paymentStyle(name(payment.getPaymentStyle()))
                .bankInstallmentCount(payment.getBankInstallmentCount())
                .subscriptionId(payment.getSubscriptionId())
                .billingCycleNumber(payment.getBillingCycleNumber())
                .verificationOnly(payment.isVerificationOnly())
                .errorCode(payment.getErrorCode())
                .errorMessage(payment.getErrorMessage())
                .purchaseId(purchase.map(Purchase::getId).orElse(null))
                .packageName(purchase.map(Purchase::getPackageName).orElse(null))
                .packageCode(purchase.map(Purchase::getPackageCode).orElse(null))
                .refundEligible(isRefundEligible(payment, purchase))
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .build();
    }

    /** Matches the payment module: what was charged minus what was already refunded. */
    static BigDecimal remainingAmount(PaymentResultDto payment) {
        BigDecimal charged = payment.getPaidPrice() != null ? payment.getPaidPrice() : payment.getPrice();
        if (charged == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal refunded = payment.getRefundedAmount() == null ? BigDecimal.ZERO : payment.getRefundedAmount();
        BigDecimal remaining = charged.subtract(refunded);
        return remaining.signum() < 0 ? BigDecimal.ZERO : remaining;
    }

    private static boolean isRefundEligible(PaymentResultDto payment, Optional<Purchase> purchase) {
        if (payment.getStatus() != PaymentStatus.SUCCESS || remainingAmount(payment).signum() <= 0) {
            return false;
        }
        return purchase.map(linked -> !refundInFlight(linked)).orElse(true);
    }

    /** A purchase refund that is running or waiting for reconciliation must not be started twice. */
    private static boolean refundInFlight(Purchase purchase) {
        return purchase.getRefundStatus() == RefundStatus.PENDING
                || purchase.getRefundStatus() == RefundStatus.NEEDS_RECONCILE;
    }

    /** Whether refunding this payment must cancel the purchase's running subscription too. */
    private static boolean isCurrentChargeOfActiveSubscription(Purchase purchase, String conversationId) {
        boolean activePaidSubscription = purchase.getStatus() == PurchaseStatus.ACTIVE
                && purchase.getPaymentStyle() == com.ael.algoryqrservice.qrservice.model.enums.PaymentStyle.SUBSCRIPTION
                && purchase.getPurchaseType() == PurchaseType.PAID
                && purchase.getRefundStatus() != RefundStatus.COMPLETED;
        if (!activePaidSubscription) {
            return false;
        }
        String current = purchase.getCurrentPeriodConversationId() != null
                && !purchase.getCurrentPeriodConversationId().isBlank()
                ? purchase.getCurrentPeriodConversationId()
                : purchase.getPaymentConversationId();
        return Objects.equals(current, conversationId);
    }

    private static Long resolveUserId(PaymentResultDto payment, Optional<Purchase> purchase) {
        return parseLong(payment.getAccountId())
                .or(() -> purchase.map(Purchase::getMerchantId))
                .orElse(null);
    }

    private static Optional<Long> parseLong(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.valueOf(raw.trim()));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String raw, String field) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Geçersiz " + field + ": " + raw);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
