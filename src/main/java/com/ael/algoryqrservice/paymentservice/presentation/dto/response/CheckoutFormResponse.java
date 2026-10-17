package com.ael.algoryqrservice.paymentservice.presentation.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutFormResponse {

    private String conversationId;
    private String token;
    private String paymentPageUrl;
    private String checkoutFormContent;
}
