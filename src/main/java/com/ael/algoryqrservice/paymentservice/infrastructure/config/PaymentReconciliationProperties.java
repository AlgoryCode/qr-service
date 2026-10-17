package com.ael.algoryqrservice.paymentservice.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payment.reconciliation")
public record PaymentReconciliationProperties(
        Long fixedDelay,
        Integer staleAfterMinutes,
        Integer batchSize
) {
    public PaymentReconciliationProperties {
        if (fixedDelay == null) {
            fixedDelay = 900_000L;
        }
        if (staleAfterMinutes == null) {
            staleAfterMinutes = 10;
        }
        if (batchSize == null) {
            batchSize = 50;
        }
    }
}
