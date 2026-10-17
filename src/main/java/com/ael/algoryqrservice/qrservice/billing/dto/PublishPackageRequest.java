package com.ael.algoryqrservice.qrservice.billing.dto;

import lombok.Data;

@Data
public class PublishPackageRequest {

    private Boolean purchasable = true;

    private Boolean active = true;
}
