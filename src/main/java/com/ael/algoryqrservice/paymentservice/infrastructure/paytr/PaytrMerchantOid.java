package com.ael.algoryqrservice.paymentservice.infrastructure.paytr;

public final class PaytrMerchantOid {

    private PaytrMerchantOid() {
    }

    public static String sanitize(String conversationId) {
        if (conversationId == null) {
            return null;
        }
        String sanitized = conversationId.replaceAll("[^A-Za-z0-9]", "");
        if (sanitized.isBlank()) {
            throw new IllegalArgumentException("PayTR merchant_oid bos olamaz");
        }
        if (sanitized.length() > 64) {
            return sanitized.substring(0, 64);
        }
        return sanitized;
    }

    public static boolean isAlphanumeric(String value) {
        return value != null && value.matches("^[A-Za-z0-9]{1,64}$");
    }
}
