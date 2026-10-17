package com.ael.algoryqrservice.paymentservice.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutFormResultDto {

    private String conversationId;
    private String token;
    private String paymentPageUrl;
    private String checkoutFormContent;
}
