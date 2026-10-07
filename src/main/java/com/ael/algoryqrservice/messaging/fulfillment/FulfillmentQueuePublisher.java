package com.ael.algoryqrservice.messaging.fulfillment;

import com.ael.algoryqrservice.config.FulfillmentRabbitProperties;
import com.ael.algoryqrservice.messaging.payment.PaymentEventTypes;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.dto.PaymentCompletedEventDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class FulfillmentQueuePublisher {

    private final RabbitTemplate rabbitTemplate;
    private final FulfillmentRabbitProperties properties;

    public void publishPayment(PaymentCompletedEventDto event) {
        if (event == null || event.getEventId() == null || event.getEventId().isBlank()) {
            return;
        }
        send(new FulfillmentQueueMessage(
                event.getEventId(),
                event.getEventType(),
                event.getOccurredAt(),
                event.getPaymentId(),
                event.getConversationId(),
                event.getServiceName(),
                event.getSourceReferenceId(),
                event.getSourceMetadata(),
                event.getPurchaseId(),
                event.getUserId(),
                event.getPackageId(),
                event.getPackageCode(),
                event.getPaymentStyle(),
                event.getBankInstallmentCount(),
                event.getSubscriptionId(),
                event.getBillingCycleNumber(),
                event.getPeriodStart(),
                event.getPeriodEnd(),
                event.getAmount(),
                event.getCurrency(),
                event.getErrorCode(),
                event.getFailureReason()
        ));
    }

    public void publishGranted(Purchase purchase) {
        if (purchase == null || purchase.getUserId() == null) {
            return;
        }
        send(lifecycle(
                PaymentEventTypes.PAYMENT_SUCCESS,
                "purchase:" + purchase.getId(),
                purchase.getUserId(),
                purchase.getId(),
                purchase.getPackageId(),
                purchase.getPackageCode(),
                purchase.getStartsAt(),
                purchase.getExpiresAt(),
                purchase.getPrice(),
                purchase.getCurrency()
        ));
    }

    public void publishRevoked(Purchase purchase) {
        if (purchase == null || purchase.getUserId() == null) {
            return;
        }
        send(lifecycle(
                PaymentEventTypes.PAYMENT_REFUNDED,
                "purchase-revoked:" + purchase.getId(),
                purchase.getUserId(),
                purchase.getId(),
                purchase.getPackageId(),
                purchase.getPackageCode(),
                purchase.getStartsAt(),
                purchase.getExpiresAt(),
                purchase.getPrice(),
                purchase.getCurrency()
        ));
    }

    private FulfillmentQueueMessage lifecycle(
            String eventType,
            String eventId,
            Long userId,
            Long referenceId,
            Long packageId,
            String packageCode,
            LocalDateTime periodStart,
            LocalDateTime periodEnd,
            BigDecimal amount,
            String currency
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("purchaseId", referenceId);
        metadata.put("userId", userId);
        if (packageId != null) {
            metadata.put("packageId", packageId);
        }
        if (packageCode != null) {
            metadata.put("packageCode", packageCode);
        }
        return new FulfillmentQueueMessage(
                eventId,
                eventType,
                LocalDateTime.now().toString(),
                null,
                null,
                "qr-service",
                referenceId == null ? null : String.valueOf(referenceId),
                metadata,
                referenceId == null ? null : String.valueOf(referenceId),
                String.valueOf(userId),
                packageId == null ? null : String.valueOf(packageId),
                packageCode,
                null,
                null,
                null,
                null,
                periodStart == null ? null : periodStart.toString(),
                periodEnd == null ? null : periodEnd.toString(),
                amount,
                currency,
                null,
                null
        );
    }

    private void send(FulfillmentQueueMessage message) {
        log.info(
                "fulfillment_queue_publish eventId={} eventType={} userId={} purchaseId={} packageCode={}",
                message.eventId(),
                message.eventType(),
                message.userId(),
                message.purchaseId(),
                message.packageCode()
        );
        rabbitTemplate.convertAndSend(properties.getExchange(), properties.getRoutingKey(), message);
    }
}
