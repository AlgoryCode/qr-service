package com.ael.algoryqrservice.model.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QrActiveResponse {
    private Long qrId;
    private Boolean active;
}
