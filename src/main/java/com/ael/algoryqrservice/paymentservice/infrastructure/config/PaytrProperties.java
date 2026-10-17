package com.ael.algoryqrservice.paymentservice.infrastructure.config;

import com.ael.algoryqrservice.paymentservice.domain.exception.PaymentProcessingException;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "paytr")
public record PaytrProperties(
        String merchantId,
        String merchantKey,
        String merchantSalt,
        String baseUrl,
        boolean testMode,
        boolean debugOn,
        int timeoutLimitMinutes
) {
    public PaytrProperties {
        merchantId = trimToNull(merchantId);
        merchantKey = trimToNull(merchantKey);
        merchantSalt = trimToNull(merchantSalt);
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://www.paytr.com";
        } else {
            baseUrl = baseUrl.trim();
        }
        if (timeoutLimitMinutes <= 0) {
            timeoutLimitMinutes = 30;
        }
    }

    public void requireConfigured() {
        if (merchantId == null || merchantKey == null || merchantSalt == null) {
            throw new PaymentProcessingException(
                    "PAYTR_NOT_CONFIGURED",
                    "PayTR merchant_id/key/salt eksik veya bos"
            );
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
