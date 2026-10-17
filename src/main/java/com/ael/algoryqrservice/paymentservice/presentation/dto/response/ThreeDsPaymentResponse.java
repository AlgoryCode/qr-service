package com.ael.algoryqrservice.paymentservice.presentation.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThreeDsPaymentResponse {

    private String conversationId;
    private String htmlContent;
}
