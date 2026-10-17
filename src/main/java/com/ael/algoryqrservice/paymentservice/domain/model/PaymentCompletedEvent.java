package com.ael.algoryqrservice.paymentservice.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCompletedEvent {

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
    private PaymentStyle paymentStyle;
    private Integer bankInstallmentCount;
    private String subscriptionId;
    private Integer billingCycleNumber;
    private String periodStart;
    private String periodEnd;
    private BigDecimal amount;
    private String currency;
    private String errorCode;
    private String failureReason;
}
