package com.ael.algoryqrservice.paymentservice.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompleteThreeDsPaymentCommand {

    private String conversationId;
    private String paymentId;
    private String conversationData;
    private String locale;
}
