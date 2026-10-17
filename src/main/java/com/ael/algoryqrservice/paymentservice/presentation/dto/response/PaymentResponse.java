package com.ael.algoryqrservice.paymentservice.presentation.dto.response;

import com.ael.algoryqrservice.paymentservice.domain.model.PaymentStatus;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentStyle;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {

    private String conversationId;
    private String paymentId;
    private String paymentTransactionId;
    private String basketId;
    private String accountId;
    private String buyerEmail;
    private String buyerName;
    private String serviceName;
    private String sourceReferenceId;
    private BigDecimal price;
    private BigDecimal paidPrice;
    private BigDecimal refundedAmount;
    private String currency;
    private PaymentStatus status;
    private PaymentType paymentType;
    private PaymentStyle paymentStyle;
    private Integer bankInstallmentCount;
    private String subscriptionId;
    private Integer billingCycleNumber;
    private boolean verificationOnly;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
