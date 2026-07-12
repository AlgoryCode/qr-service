package com.ael.algoryqrservice.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class QrResponse {
    private Long qrId;
    private String imgSrc;
    private String publicUrl;
    private Long menuId;
    private String urlMode;
}
