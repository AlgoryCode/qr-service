package com.ael.algoryqrservice.paymentservice.domain.model;

import java.util.List;

public record InstallmentOptions(
        String binNumber,
        String cardAssociation,
        String cardFamily,
        String bankName,
        boolean forceThreeDs,
        List<InstallmentOption> options
) {
}
