package com.ael.algoryqrservice.paymentservice.application.mapper;

import com.ael.algoryqrservice.paymentservice.application.dto.CheckoutFormResultDto;
import com.ael.algoryqrservice.paymentservice.application.dto.PaymentResultDto;
import com.ael.algoryqrservice.paymentservice.application.dto.RefundResultDto;
import com.ael.algoryqrservice.paymentservice.application.dto.ThreeDsPaymentResultDto;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentGatewayResult;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentStatus;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentTransaction;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class PaymentApplicationMapper {

    private static final String CARD_TOKEN_MISSING_ERROR = "CARD_TOKEN_MISSING";

    public PaymentResultDto toPaymentResultDto(PaymentTransaction transaction) {
        return PaymentResultDto.builder()
                .conversationId(transaction.getConversationId())
                .paymentId(transaction.getPaymentId())
                .paymentTransactionId(transaction.getPaymentTransactionId())
                .basketId(transaction.getBasketId())
                .accountId(transaction.getAccountId())
                .buyerEmail(transaction.getBuyerEmail())
                .buyerName(transaction.getBuyerName())
                .serviceName(transaction.getServiceName())
                .sourceReferenceId(transaction.getSourceReferenceId())
                .price(transaction.getPrice())
                .paidPrice(transaction.getPaidPrice())
                .refundedAmount(transaction.getRefundedAmount() == null ? BigDecimal.ZERO : transaction.getRefundedAmount())
                .currency(transaction.getCurrency())
                .status(resolveReportedStatus(transaction))
                .paymentType(transaction.getPaymentType())
                .paymentStyle(transaction.getPaymentStyle())
                .bankInstallmentCount(transaction.getBankInstallmentCount())
                .subscriptionId(transaction.getSubscriptionId())
                .billingCycleNumber(transaction.getBillingCycleNumber())
                .verificationOnly(transaction.isVerificationOnly())
                .errorCode(transaction.getErrorCode())
                .errorMessage(transaction.getErrorMessage())
                .createdAt(transaction.getCreatedAt())
                .updatedAt(transaction.getUpdatedAt())
                .build();
    }

    private PaymentStatus resolveReportedStatus(PaymentTransaction transaction) {
        if (transaction.isVerificationOnly()
                && CARD_TOKEN_MISSING_ERROR.equals(transaction.getErrorCode())) {
            return PaymentStatus.FAILURE;
        }
        return transaction.getStatus();
    }

    public CheckoutFormResultDto toCheckoutFormResultDto(PaymentGatewayResult gatewayResult) {
        return CheckoutFormResultDto.builder()
                .conversationId(gatewayResult.getConversationId())
                .token(gatewayResult.getToken())
                .paymentPageUrl(gatewayResult.getPaymentPageUrl())
                .checkoutFormContent(gatewayResult.getCheckoutFormContent())
                .build();
    }

    public ThreeDsPaymentResultDto toThreeDsPaymentResultDto(PaymentGatewayResult gatewayResult) {
        return ThreeDsPaymentResultDto.builder()
                .conversationId(gatewayResult.getConversationId())
                .htmlContent(gatewayResult.getHtmlContent())
                .build();
    }

    public RefundResultDto toRefundResultDto(
            PaymentGatewayResult gatewayResult,
            String paymentTransactionId,
            BigDecimal totalRefundedAmount
    ) {
        return RefundResultDto.builder()
                .conversationId(gatewayResult.getConversationId())
                .paymentTransactionId(paymentTransactionId)
                .refundedPrice(gatewayResult.getPaidPrice())
                .totalRefundedAmount(totalRefundedAmount)
                .status(gatewayResult.getStatus())
                .build();
    }
}
