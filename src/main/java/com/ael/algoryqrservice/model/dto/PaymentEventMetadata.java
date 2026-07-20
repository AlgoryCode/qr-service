package com.ael.algoryqrservice.model.dto;

import com.ael.algoryqrservice.exception.InvalidPaymentEventException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

public record PaymentEventMetadata(
        Long purchaseId,
        Long userId,
        Long packageId,
        String packageCode,
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
                value(metadata, "installmentId", defaultInstallmentId(event)),
                integerValue(metadata, "installmentNumber", defaultInstallmentNumber(event, metadata)),
                integerValue(metadata, "installmentCount", defaultInstallmentCount(event, metadata)),
                dateTimeValue(metadata, "periodStart", event.getPeriodStart(), false),
                dateTimeValue(metadata, "periodEnd", event.getPeriodEnd(), true)
        );
    }

    private static String defaultInstallmentId(PaymentCompletedEventDto event) {
        String installmentId = first(event.getInstallmentId(), event.getPaymentId());
        if (installmentId != null && !installmentId.isBlank()) {
            return installmentId;
        }
        return first(event.getConversationId(), "unknown");
    }

    private static Integer defaultInstallmentCount(
            PaymentCompletedEventDto event,
            Map<String, Object> metadata
    ) {
        if (event.getInstallmentCount() != null) {
            return event.getInstallmentCount();
        }
        Object metadataValue = metadata.get("installmentCount");
        if (metadataValue != null && !String.valueOf(metadataValue).isBlank()) {
            return Integer.valueOf(String.valueOf(metadataValue));
        }
        return 1;
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

    private static Integer defaultInstallmentNumber(
            PaymentCompletedEventDto event,
            Map<String, Object> metadata
    ) {
        if (event.getBillingCycleNumber() != null && event.getBillingCycleNumber() > 0) {
            return event.getBillingCycleNumber();
        }
        if (event.getInstallmentNumber() != null) {
            return event.getInstallmentNumber();
        }
        Object metadataValue = metadata.get("installmentNumber");
        if (metadataValue != null && !String.valueOf(metadataValue).isBlank()) {
            return Integer.valueOf(String.valueOf(metadataValue));
        }
        Object cycleValue = metadata.get("billingCycleNumber");
        if (cycleValue != null && !String.valueOf(cycleValue).isBlank()) {
            return Integer.valueOf(String.valueOf(cycleValue));
        }
        return 1;
    }

    private static String packageCodeValue(Map<String, Object> metadata, String fallback) {
        String packageCode = value(metadata, "packageCode", fallback);
        if (!packageCode.matches("^[A-Z][A-Z0-9_]*$")) {
            throw new InvalidPaymentEventException("Payment event metadata is invalid: packageCode");
        }
        return packageCode;
    }
}
