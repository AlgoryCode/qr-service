package com.ael.algoryqrservice.fulfillmentservice.application;

import com.ael.algoryqrservice.qrservice.billing.dto.PaymentCompletedEventDto;
import com.ael.algoryqrservice.fulfillmentservice.application.service.FulfillmentCommandFactory;
import com.ael.algoryqrservice.fulfillmentservice.application.service.FulfillmentService;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.PaymentCompletedEvent;
import com.ael.algoryqrservice.qrservice.messaging.fulfillment.FulfillmentQueueMessage;
import com.ael.algoryqrservice.qrservice.messaging.payment.PaymentEventTypes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class FulfillmentPaymentBridge {

    private static final Set<String> FULFILL = Set.of(
            PaymentEventTypes.PAYMENT_SUCCESS,
            PaymentEventTypes.PAYMENT_INSTALLMENT_PAID,
            PaymentEventTypes.PAYMENT_SUBSCRIPTION_PAID
    );

    private static final Set<String> IGNORE = Set.of(
            PaymentEventTypes.PAYMENT_FAILED,
            PaymentEventTypes.PAYMENT_SUBSCRIPTION_FAILED,
            PaymentEventTypes.PAYMENT_SUBSCRIPTION_PAST_DUE,
            PaymentEventTypes.PAYMENT_REFUNDED,
            PaymentEventTypes.PAYMENT_INSTALLMENT_FAILED,
            PaymentEventTypes.PAYMENT_INSTALLMENT_OVERDUE
    );

    private final FulfillmentService fulfillmentService;
    private final FulfillmentCommandFactory commandFactory;

    public void apply(PaymentCompletedEventDto event) {
        if (event == null || event.getEventType() == null) {
            return;
        }
        apply(toDomain(event));
    }

    public void apply(FulfillmentQueueMessage message) {
        if (message == null || message.eventType() == null) {
            return;
        }
        apply(toDomain(message));
    }

    private void apply(PaymentCompletedEvent event) {
        String eventType = event.eventType();
        if (FULFILL.contains(eventType)) {
            fulfillmentService.fulfillPaidPackage(commandFactory.fromPaymentEvent(event));
            return;
        }
        if (IGNORE.contains(eventType)) {
            log.info(
                    "Payment event acknowledged without fulfillment. eventId={} eventType={} purchaseId={}",
                    event.eventId(),
                    eventType,
                    event.sourceReferenceId()
            );
            return;
        }
        log.warn("Unsupported payment event for fulfillment bridge. eventId={} eventType={}", event.eventId(), eventType);
    }

    private static PaymentCompletedEvent toDomain(PaymentCompletedEventDto event) {
        return new PaymentCompletedEvent(
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
        );
    }

    private static PaymentCompletedEvent toDomain(FulfillmentQueueMessage message) {
        return new PaymentCompletedEvent(
                message.eventId(),
                message.eventType(),
                message.occurredAt(),
                message.paymentId(),
                message.conversationId(),
                message.serviceName(),
                message.sourceReferenceId(),
                message.sourceMetadata(),
                message.purchaseId(),
                message.merchantId(),
                message.packageId(),
                message.packageCode(),
                message.paymentStyle(),
                message.bankInstallmentCount(),
                message.subscriptionId(),
                message.billingCycleNumber(),
                message.periodStart(),
                message.periodEnd(),
                message.amount(),
                message.currency(),
                message.errorCode(),
                message.failureReason()
        );
    }
}
