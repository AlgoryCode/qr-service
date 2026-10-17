package com.ael.algoryqrservice.qrservice.billing.dto;

import com.ael.algoryqrservice.qrservice.model.enums.PurchaseLogAction;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PurchaseLogResponse {

    private Long id;
    private Long purchaseId;
    private Long merchantId;
    private PurchaseLogAction action;
    private String message;
    private LocalDateTime createdAt;
}
