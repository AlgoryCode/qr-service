package com.ael.algoryqrservice.client.dto;

import lombok.Data;

@Data
public class PaymentCheckoutFormResponse {

    private String conversationId;
    private String token;
    private String paymentPageUrl;
    private String checkoutFormContent;
}
