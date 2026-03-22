package com.ael.algoryqrservice.model.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class QrListResponse {
    private Long qrId;
    private Long userId;
    private String qrName;
    private String imgSrc;
    private Map<String, Object> details;
    private LocalDateTime createdAt;
}
