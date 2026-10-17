package com.ael.algoryqrservice.paymentservice.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentGatewayResult {

    private String conversationId;
    private String paymentId;
    private String paymentTransactionId;
    private String status;
    private String paymentStatus;
    private String errorCode;
    private String errorMessage;
    private String htmlContent;
    private String paymentPageUrl;
    private String checkoutFormContent;
    private String token;
    private BigDecimal price;
    private BigDecimal paidPrice;
    @ToString.Exclude
    private String cardUserKey;
    @ToString.Exclude
    private String cardToken;
    private String lastFourDigits;
    private Integer installmentCount;
    private boolean signatureValid;
}
