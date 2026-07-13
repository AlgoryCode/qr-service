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
    private BigDecimal amount;
    private String currency;
}
