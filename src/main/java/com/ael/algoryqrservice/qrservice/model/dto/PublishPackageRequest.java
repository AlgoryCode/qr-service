package com.ael.algoryqrservice.qrservice.model.dto;

import lombok.Data;

@Data
public class PublishPackageRequest {

    private Boolean purchasable = true;

    private Boolean active = true;
}
