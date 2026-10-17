package com.ael.algoryqrservice.qrservice.client.payment;

import com.ael.algoryqrservice.paymentservice.application.dto.PaymentResultDto;
import com.ael.algoryqrservice.paymentservice.application.dto.RefundResultDto;
import com.ael.algoryqrservice.paymentservice.domain.model.InstallmentOption;
import com.ael.algoryqrservice.paymentservice.presentation.dto.response.PaymentMethodResponse;
import com.ael.algoryqrservice.paymentservice.presentation.dto.response.SubscriptionResponse;
import com.ael.algoryqrservice.qrservice.client.dto.BillingPaymentDtos;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class PaymentBillingResponseMapper {

    public BillingPaymentDtos.StoredCardCharge toStoredCardCharge(PaymentResultDto result) {
        return new BillingPaymentDtos.StoredCardCharge(
                result.getConversationId(),
                result.getStatus() == null ? null : result.getStatus().name()
        );
    }

    public BillingPaymentDtos.RefundablePayment toRefundablePayment(PaymentResultDto result) {
        BigDecimal paidPrice = result.getPaidPrice() == null ? BigDecimal.ZERO : result.getPaidPrice();
        BigDecimal refundedAmount = result.getRefundedAmount() == null ? BigDecimal.ZERO : result.getRefundedAmount();
        return new BillingPaymentDtos.RefundablePayment(
                result.getConversationId(),
                result.getPaymentId(),
                result.getPaymentTransactionId(),
                result.getStatus() == null ? null : result.getStatus().name(),
                paidPrice,
                refundedAmount,
                paidPrice.subtract(refundedAmount).max(BigDecimal.ZERO)
        );
    }

    public BillingPaymentDtos.PaymentDetail toPaymentDetail(PaymentResultDto result) {
        BigDecimal paidPrice = result.getPaidPrice() == null ? BigDecimal.ZERO : result.getPaidPrice();
        BigDecimal refundedAmount = result.getRefundedAmount() == null ? BigDecimal.ZERO : result.getRefundedAmount();
        return new BillingPaymentDtos.PaymentDetail(
                result.getConversationId(),
                result.getPaymentId(),
                result.getPaymentTransactionId(),
                result.getBasketId(),
                result.getAccountId(),
                result.getBuyerEmail(),
                result.getBuyerName(),
                result.getServiceName(),
                result.getSourceReferenceId(),
                result.getPrice(),
                paidPrice,
                refundedAmount,
                paidPrice.subtract(refundedAmount).max(BigDecimal.ZERO),
                result.getCurrency(),
                result.getStatus() == null ? null : result.getStatus().name(),
                result.getPaymentType() == null ? null : result.getPaymentType().name(),
                result.getPaymentStyle() == null ? null : result.getPaymentStyle().name(),
                result.getBankInstallmentCount(),
                result.getSubscriptionId(),
                result.getBillingCycleNumber(),
                result.isVerificationOnly(),
                result.getErrorCode(),
                result.getErrorMessage(),
                result.getCreatedAt(),
                result.getUpdatedAt()
        );
    }

    public BillingPaymentDtos.RefundResult toRefundResult(RefundResultDto result) {
        return new BillingPaymentDtos.RefundResult(
                result.getConversationId(),
                result.getPaymentTransactionId(),
                result.getRefundedPrice(),
                result.getStatus()
        );
    }

    public BillingPaymentDtos.PaymentMethod toPaymentMethod(PaymentMethodResponse response) {
        return new BillingPaymentDtos.PaymentMethod(
                response.id() == null ? null : String.valueOf(response.id()),
                response.alias(),
                response.brand(),
                response.last4(),
                null,
                null
        );
    }

    public BillingPaymentDtos.InstallmentOption toInstallmentOption(InstallmentOption option) {
        return new BillingPaymentDtos.InstallmentOption(
                option.installmentCount(),
                option.totalAmount(),
                option.installmentAmount()
        );
    }

    public BillingPaymentDtos.Subscription toSubscription(SubscriptionResponse response) {
        return new BillingPaymentDtos.Subscription(
                response.id(),
                response.paymentMethodId(),
                response.status(),
                response.amount(),
                response.currency(),
                response.completedCycleCount(),
                response.totalCycleCount(),
                response.nextChargeAt(),
                null
        );
    }
}
