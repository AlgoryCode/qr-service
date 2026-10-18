package com.ael.algoryqrservice.fulfillmentservice.application.service;

import com.ael.algoryqrservice.fulfillmentservice.domain.exception.InvalidPaymentEventException;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.FulfillmentCommand;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.PaymentCompletedEvent;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;

@Component
public class FulfillmentCommandFactory {

    public FulfillmentCommand fromPaymentEvent(PaymentCompletedEvent event) {
        if (event == null) {
            throw new InvalidPaymentEventException("Payment event is required");
        }
        if (!StringUtils.hasText(event.eventId())) {
            throw new InvalidPaymentEventException("Payment event eventId is required");
        }
        if (!StringUtils.hasText(event.eventType())) {
            throw new InvalidPaymentEventException("Payment event eventType is required");
        }

        Map<String, Object> metadata = event.sourceMetadata() == null ? Map.of() : event.sourceMetadata();
        return new FulfillmentCommand(
                event.eventId(),
                event.eventType(),
                requireLong(metadata, "purchaseId", first(event.purchaseId(), event.sourceReferenceId())),
                requireMerchantId(metadata, event.merchantId()),
                optionalLong(metadata, "packageId", event.packageId()),
                optionalText(metadata, "packageCode", event.packageCode()),
                optionalText(metadata, "purchaseConversationId", event.conversationId()),
                first(event.paymentId(), event.conversationId()),
                event.subscriptionId(),
                event.billingCycleNumber(),
                parseDate(event.periodStart()),
                parseDate(event.periodEnd()),
                event.amount() == null ? BigDecimal.ZERO : event.amount(),
                event.currency(),
                metadata
        );
    }

    private static LocalDate parseDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            if (value.length() > 10) {
                return LocalDateTime.parse(value).toLocalDate();
            }
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new InvalidPaymentEventException("Payment event date is invalid: " + value, exception);
        }
    }

    private static String first(String primary, String fallback) {
        if (StringUtils.hasText(primary)) {
            return primary;
        }
        return fallback;
    }

    private static Long requireMerchantId(Map<String, Object> metadata, String eventMerchantId) {
        return requireLong(metadata, "merchantId", eventMerchantId);
    }

    private static Long requireLong(Map<String, Object> metadata, String key, String fallback) {
        String value = metadata.get(key) == null ? fallback : String.valueOf(metadata.get(key));
        if (!StringUtils.hasText(value)) {
            throw new InvalidPaymentEventException("Payment event metadata is missing: " + key);
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new InvalidPaymentEventException("Payment event metadata is invalid: " + key, exception);
        }
    }

    private static Long optionalLong(Map<String, Object> metadata, String key, String fallback) {
        String value = metadata.get(key) == null ? fallback : String.valueOf(metadata.get(key));
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new InvalidPaymentEventException("Payment event metadata is invalid: " + key, exception);
        }
    }

    private static String optionalText(Map<String, Object> metadata, String key, String fallback) {
        Object value = metadata.get(key);
        if (value != null && StringUtils.hasText(String.valueOf(value))) {
            return String.valueOf(value);
        }
        return StringUtils.hasText(fallback) ? fallback : null;
    }
}
