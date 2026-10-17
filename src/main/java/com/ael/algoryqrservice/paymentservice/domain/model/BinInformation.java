package com.ael.algoryqrservice.paymentservice.domain.model;

public record BinInformation(
        String binNumber,
        String cardType,
        String cardAssociation,
        String cardFamily,
        String bankName,
        Long bankCode,
        boolean commercial
) {
}
