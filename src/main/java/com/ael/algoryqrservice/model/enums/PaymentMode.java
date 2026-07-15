package com.ael.algoryqrservice.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum PaymentMode {
    DIRECT,
    THREE_DS;

    @JsonCreator
    public static PaymentMode fromValue(String value) {
        if ("3DS".equalsIgnoreCase(value)) {
            return THREE_DS;
        }
        return value == null ? null : valueOf(value.toUpperCase());
    }
}
