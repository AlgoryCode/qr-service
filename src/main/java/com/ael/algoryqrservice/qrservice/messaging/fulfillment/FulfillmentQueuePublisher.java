package com.ael.algoryqrservice.qrservice.messaging.fulfillment;

import com.ael.algoryqrservice.qrservice.billing.service.RecurringAddonBilling;
import com.ael.algoryqrservice.fulfillmentservice.application.FulfillmentPaymentBridge;
import com.ael.algoryqrservice.qrservice.billing.dto.PaymentCompletedEventDto;
import com.ael.algoryqrservice.qrservice.billing.service.entitlement.AddonEntitlementService;
import com.ael.algoryqrservice.qrservice.messaging.payment.PaymentEventTypes;
import com.ael.algoryqrservice.qrservice.model.Purchase;
import com.ael.algoryqrservice.fulfillmentservice.application.service.SubscriptionLifecycleService;
import com.ael.algoryqrservice.qrservice.util.AppTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class FulfillmentQueuePublisher {

    private final FulfillmentPaymentBridge fulfillmentPaymentBridge;
    private final AddonEntitlementService addonEntitlementService;
    private final SubscriptionLifecycleService subscriptionLifecycleService;
    private final RecurringAddonBilling recurringAddonBilling;

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
                event.getMerchantId(),
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
        if (purchase == null || purchase.getMerchantId() == null) {
            return;
        }
        if (AddonEntitlementService.isAddon(purchase)) {
            addonEntitlementService.grant(purchase);
            recurringAddonBilling.sync(purchase.getMerchantId());
            return;
        }
        // The event id carries the period end: an operator extension or a recalculated period must
        // reach the package again, while a replay of the same grant stays a no-op.
        send(lifecycle(
                PaymentEventTypes.PAYMENT_SUCCESS,
                "purchase:" + purchase.getId() + ":" + purchase.getExpiresAt(),
                purchase.getMerchantId(),
                purchase.getId(),
                purchase.getPackageId(),
                purchase.getPackageCode(),
                purchase.getStartsAt(),
                purchase.getExpiresAt(),
                purchase.getPrice(),
                purchase.getCurrency()
        ));
    }

    /**
     * The purchase was refunded or cancelled with its money returned. An add-on loses its
     * entitlements; a package that is the merchant's current one ends now and the merchant falls
     * back to the free package. A plain expiry is not a revocation: the lifecycle job handles it.
     */
    public void publishRevoked(Purchase purchase) {
        if (purchase == null || purchase.getMerchantId() == null) {
            return;
        }
        if (AddonEntitlementService.isAddon(purchase)) {
            addonEntitlementService.revoke(purchase);
            recurringAddonBilling.sync(purchase.getMerchantId());
            return;
        }
        log.info("fulfillment_package_revoked merchantId={} purchaseId={} packageCode={}",
                purchase.getMerchantId(), purchase.getId(), purchase.getPackageCode());
        subscriptionLifecycleService.onRefund(purchase.getMerchantId(), purchase.getId());
    }

    /** A renewal could not be charged: keep the current paid package open until {@code graceEndsAt}. */
    public void publishPaymentOverdue(Purchase purchase, LocalDateTime graceEndsAt, String reason) {
        if (purchase == null || purchase.getMerchantId() == null || AddonEntitlementService.isAddon(purchase)) {
            return;
        }
        subscriptionLifecycleService.enterGrace(
                purchase.getMerchantId(),
                purchase.getId(),
                graceEndsAt == null ? null : graceEndsAt.atZone(AppTime.ZONE).toInstant(),
                reason);
    }

    private FulfillmentQueueMessage lifecycle(
            String eventType,
            String eventId,
            Long merchantId,
            Long purchaseId,
            Long packageId,
            String packageCode,
            LocalDateTime periodStart,
            LocalDateTime periodEnd,
            BigDecimal amount,
            String currency
    ) {
        return new FulfillmentQueueMessage(
                eventId,
                eventType,
                LocalDateTime.now().toString(),
                null,
                null,
                null,
                String.valueOf(purchaseId),
                null,
                purchaseId == null ? null : String.valueOf(purchaseId),
                merchantId == null ? null : String.valueOf(merchantId),
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
                "fulfillment_payment_apply eventId={} eventType={} merchantId={} purchaseId={} packageCode={}",
                message.eventId(),
                message.eventType(),
                message.merchantId(),
                message.purchaseId(),
                message.packageCode()
        );
        fulfillmentPaymentBridge.apply(message);
    }
}
