package com.ael.algoryqrservice.model.dto;

import com.ael.algoryqrservice.exception.InvalidPaymentEventException;
import com.ael.algoryqrservice.model.enums.PackageCode;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.Map;

public record PaymentEventMetadata(
        Long purchaseId,
        Long userId,
        Long packageId,
        PackageCode packageCode,
        String purchaseConversationId,
        String installmentId,
        Integer installmentNumber,
        Integer installmentCount,
        LocalDateTime periodStart,
        LocalDateTime periodEnd
) {

    public static PaymentEventMetadata from(PaymentCompletedEventDto event) {
        Map<String, Object> metadata = event.getSourceMetadata();
        if (metadata == null) {
            throw new InvalidPaymentEventException("Payment event metadata is missing");
        }
        return new PaymentEventMetadata(
                longValue(metadata, "purchaseId", first(event.getPurchaseId(), event.getSourceReferenceId())),
                longValue(metadata, "userId", event.getUserId()),
                longValue(metadata, "packageId", event.getPackageId()),
                packageCodeValue(metadata, event.getPackageCode()),
                value(metadata, "purchaseConversationId", event.getConversationId()),
                value(metadata, "installmentId", first(event.getInstallmentId(), event.getPaymentId())),
                integerValue(metadata, "installmentNumber", event.getInstallmentNumber()),
                integerValue(metadata, "installmentCount", event.getInstallmentCount()),
                dateTimeValue(metadata, "periodStart", event.getPeriodStart(), false),
                dateTimeValue(metadata, "periodEnd", event.getPeriodEnd(), true)
        );
    }

    private static String value(Map<String, Object> metadata, String key, Object fallback) {
        Object value = metadata.get(key);
        if (value == null) {
            value = fallback;
        }
        if (value == null || String.valueOf(value).isBlank()) {
            throw new InvalidPaymentEventException("Payment event metadata is missing: " + key);
        }
        return String.valueOf(value);
    }

    private static Long longValue(Map<String, Object> metadata, String key, String fallback) {
        String value = metadata.get(key) == null ? fallback : String.valueOf(metadata.get(key));
        if (value == null || value.isBlank()) {
            throw new InvalidPaymentEventException("Payment event metadata is missing: " + key);
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new InvalidPaymentEventException("Payment event metadata is invalid: " + key);
        }
    }

    private static Integer integerValue(Map<String, Object> metadata, String key, Integer fallback) {
        try {
            return Integer.valueOf(value(metadata, key, fallback));
        } catch (NumberFormatException exception) {
            throw new InvalidPaymentEventException("Payment event metadata is invalid: " + key);
        }
    }

    private static LocalDateTime dateTimeValue(
            Map<String, Object> metadata,
            String key,
            String fallback,
            boolean inclusiveEnd
    ) {
        String value = value(metadata, key, fallback);
        try {
            return LocalDateTime.parse(value);
        } catch (RuntimeException exception) {
            try {
                LocalDate date = LocalDate.parse(value);
                return inclusiveEnd ? date.plusDays(1).atStartOfDay() : date.atStartOfDay();
            } catch (RuntimeException dateException) {
                throw new InvalidPaymentEventException("Payment event metadata is invalid: " + key);
            }
        }
    }

    private static String first(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private static PackageCode packageCodeValue(Map<String, Object> metadata, String fallback) {
        try {
            return PackageCode.valueOf(value(metadata, "packageCode", fallback));
        } catch (IllegalArgumentException exception) {
            throw new InvalidPaymentEventException("Payment event metadata is invalid: packageCode");
        }
    }
}
