package com.ael.algoryqrservice.model.dto;

import com.ael.algoryqrservice.model.enums.FulfillmentStatus;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.math.BigDecimal;

@Value
@Builder
public class PurchaseFulfillmentResponse {
    Long id;
    String installmentId;
    Integer installmentNumber;
    Integer installmentCount;
    FulfillmentStatus status;
    LocalDateTime startsAt;
    LocalDateTime expiresAt;
    LocalDateTime dueAt;
    BigDecimal amount;
    String currency;
    String failureReason;
}
