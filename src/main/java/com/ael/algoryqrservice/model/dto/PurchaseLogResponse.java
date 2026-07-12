package com.ael.algoryqrservice.model.dto;

import com.ael.algoryqrservice.model.enums.PurchaseLogAction;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PurchaseLogResponse {

    private Long id;
    private Long purchaseId;
    private Long userId;
    private PurchaseLogAction action;
    private String message;
    private LocalDateTime createdAt;
}
