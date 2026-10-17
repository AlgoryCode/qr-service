package com.ael.algoryqrservice.paymentservice.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentTransaction {

    private Long id;
    private String conversationId;
    private PaymentProvider provider;
    private String paymentId;
    private String paymentTransactionId;
    private String gatewayToken;
    private String accountId;
    private String basketId;
    private BigDecimal price;
    private BigDecimal paidPrice;
    private BigDecimal refundedAmount;
    private String currency;
    private PaymentStatus status;
    private PaymentType paymentType;
    private String errorCode;
    private String errorMessage;
    private String serviceName;
    private String sourceReferenceId;
    private String sourceMetadataJson;
    private String buyerEmail;
    private String buyerName;
    private PaymentStyle paymentStyle;
    private Integer bankInstallmentCount;
    private String subscriptionId;
    private Integer billingCycleNumber;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private boolean verificationOnly;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
