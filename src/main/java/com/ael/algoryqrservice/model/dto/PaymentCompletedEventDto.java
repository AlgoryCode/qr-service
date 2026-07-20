package com.ael.algoryqrservice.model.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class PaymentCompletedEventDto {

    private String eventId;
    private String eventType;
    private String occurredAt;
    private String paymentId;
    private String conversationId;
    private String serviceName;
    private String sourceReferenceId;
    private Map<String, Object> sourceMetadata;
    private String purchaseId;
    private String userId;
    private String packageId;
    private String packageCode;
    private String installmentId;
    private Integer installmentNumber;
    private Integer installmentCount;
    private Integer billingCycleNumber;
    private String paymentStyle;
    private Integer bankInstallmentCount;
    private String subscriptionId;
    private String subscriptionStatus;
    private String periodStart;
    private String periodEnd;
    private BigDecimal amount;
    private String currency;
    private String failureReason;
}
