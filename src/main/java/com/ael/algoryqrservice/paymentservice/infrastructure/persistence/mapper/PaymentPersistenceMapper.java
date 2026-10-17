package com.ael.algoryqrservice.paymentservice.infrastructure.persistence.mapper;

import com.ael.algoryqrservice.paymentservice.domain.model.PaymentProvider;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentTransaction;
import com.ael.algoryqrservice.paymentservice.infrastructure.persistence.entity.PaymentEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class PaymentPersistenceMapper {

    public PaymentEntity toEntity(PaymentTransaction transaction) {
        return PaymentEntity.builder()
                .id(transaction.getId())
                .conversationId(transaction.getConversationId())
                .provider(transaction.getProvider() == null ? PaymentProvider.PAYTR : transaction.getProvider())
                .paymentId(transaction.getPaymentId())
                .paymentTransactionId(transaction.getPaymentTransactionId())
                .gatewayToken(transaction.getGatewayToken())
                .accountId(transaction.getAccountId())
                .basketId(transaction.getBasketId())
                .price(transaction.getPrice())
                .paidPrice(transaction.getPaidPrice())
                .refundedAmount(transaction.getRefundedAmount() == null ? BigDecimal.ZERO : transaction.getRefundedAmount())
                .currency(transaction.getCurrency())
                .status(transaction.getStatus())
                .paymentType(transaction.getPaymentType())
                .errorCode(transaction.getErrorCode())
                .errorMessage(transaction.getErrorMessage())
                .serviceName(transaction.getServiceName())
                .sourceReferenceId(transaction.getSourceReferenceId())
                .sourceMetadata(transaction.getSourceMetadataJson())
                .buyerEmail(transaction.getBuyerEmail())
                .buyerName(transaction.getBuyerName())
                .paymentStyle(transaction.getPaymentStyle())
                .bankInstallmentCount(transaction.getBankInstallmentCount())
                .subscriptionId(transaction.getSubscriptionId())
                .billingCycleNumber(transaction.getBillingCycleNumber())
                .periodStart(transaction.getPeriodStart())
                .periodEnd(transaction.getPeriodEnd())
                .verificationOnly(transaction.isVerificationOnly())
                .createdAt(transaction.getCreatedAt())
                .updatedAt(transaction.getUpdatedAt())
                .build();
    }

    public PaymentTransaction toDomain(PaymentEntity entity) {
        return PaymentTransaction.builder()
                .id(entity.getId())
                .conversationId(entity.getConversationId())
                .provider(entity.getProvider() == null ? PaymentProvider.PAYTR : entity.getProvider())
                .paymentId(entity.getPaymentId())
                .paymentTransactionId(entity.getPaymentTransactionId())
                .gatewayToken(entity.getGatewayToken())
                .accountId(entity.getAccountId())
                .basketId(entity.getBasketId())
                .price(entity.getPrice())
                .paidPrice(entity.getPaidPrice())
                .refundedAmount(entity.getRefundedAmount() == null ? BigDecimal.ZERO : entity.getRefundedAmount())
                .currency(entity.getCurrency())
                .status(entity.getStatus())
                .paymentType(entity.getPaymentType())
                .errorCode(entity.getErrorCode())
                .errorMessage(entity.getErrorMessage())
                .serviceName(entity.getServiceName())
                .sourceReferenceId(entity.getSourceReferenceId())
                .sourceMetadataJson(entity.getSourceMetadata())
                .buyerEmail(entity.getBuyerEmail())
                .buyerName(entity.getBuyerName())
                .paymentStyle(entity.getPaymentStyle())
                .bankInstallmentCount(entity.getBankInstallmentCount())
                .subscriptionId(entity.getSubscriptionId())
                .billingCycleNumber(entity.getBillingCycleNumber())
                .periodStart(entity.getPeriodStart())
                .periodEnd(entity.getPeriodEnd())
                .verificationOnly(entity.isVerificationOnly())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
