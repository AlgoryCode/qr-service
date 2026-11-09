package com.ael.algoryqrservice.qrservice.qr.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class QrRequest {
    private Long merchantId;

    private Long purchaseId;

    private String qrName;

    private String type;

    private Map<String, Object> details;

    private Boolean trackingEnabled;

    /** "dynamic" or "static"; when absent the default for the QR's type applies. */
    private String qrMode;

}
