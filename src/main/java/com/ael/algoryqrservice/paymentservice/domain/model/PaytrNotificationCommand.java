package com.ael.algoryqrservice.paymentservice.domain.model;

public record PaytrNotificationCommand(
        String merchantOid,
        String status,
        String totalAmount,
        String hash,
        String paymentAmount,
        String failedReasonCode,
        String failedReasonMsg,
        String paymentType,
        String installmentCount,
        String utoken,
        String ctoken,
        String maskedPan
) {
}
