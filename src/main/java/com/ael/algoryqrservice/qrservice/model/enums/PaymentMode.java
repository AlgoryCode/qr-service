package com.ael.algoryqrservice.qrservice.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum PaymentMode {
    DIRECT,
    THREE_DS,
    CHECKOUT_FORM;

    @JsonCreator
    public static PaymentMode fromValue(String value) {
        if ("3DS".equalsIgnoreCase(value)) {
            return THREE_DS;
        }
        return value == null ? null : valueOf(value.toUpperCase());
    }
}
