package com.ael.algoryqrservice.model.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class QrListResponse {
    private Long qrId;
    private Long userId;
    private String qrName;
    private String imgSrc;
    private JsonNode details;
    private LocalDateTime createdAt;
}
