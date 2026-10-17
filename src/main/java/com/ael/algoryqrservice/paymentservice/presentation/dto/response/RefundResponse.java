package com.ael.algoryqrservice.paymentservice.presentation.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundResponse {

    private String conversationId;
    private String paymentTransactionId;
    private BigDecimal refundedPrice;
    private BigDecimal totalRefundedAmount;
    private String status;
}
