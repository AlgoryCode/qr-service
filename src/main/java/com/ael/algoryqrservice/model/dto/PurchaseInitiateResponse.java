package com.ael.algoryqrservice.model.dto;

import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PurchaseInitiateResponse {

    private Long purchaseId;
    private PurchaseStatus status;
    private String conversationId;
    private String paymentHtml;
}
