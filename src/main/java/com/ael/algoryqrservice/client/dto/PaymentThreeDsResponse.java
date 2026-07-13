package com.ael.algoryqrservice.client.dto;

import lombok.Data;

@Data
public class PaymentThreeDsResponse {

    private String conversationId;
    private String htmlContent;
}
