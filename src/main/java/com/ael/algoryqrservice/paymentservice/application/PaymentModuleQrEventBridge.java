package com.ael.algoryqrservice.paymentservice.application;

import com.ael.algoryqrservice.paymentservice.domain.model.PaymentCompletedEvent;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentStyle;
import com.ael.algoryqrservice.qrservice.billing.dto.PaymentCompletedEventDto;
import com.ael.algoryqrservice.qrservice.messaging.payment.handler.PaymentEventHandlerRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentModuleQrEventBridge {

    private final PaymentEventHandlerRegistry paymentEventHandlerRegistry;

    public void dispatch(PaymentCompletedEvent event) {
        if (event == null) {
            return;
        }
        paymentEventHandlerRegistry.dispatch(toDto(event));
    }

    private static PaymentCompletedEventDto toDto(PaymentCompletedEvent event) {
        PaymentCompletedEventDto dto = new PaymentCompletedEventDto();
        dto.setEventId(event.getEventId());
        dto.setEventType(event.getEventType());
        dto.setOccurredAt(event.getOccurredAt());
        dto.setPaymentId(event.getPaymentId());
        dto.setConversationId(event.getConversationId());
        dto.setServiceName(event.getServiceName());
        dto.setSourceReferenceId(event.getSourceReferenceId());
        dto.setSourceMetadata(event.getSourceMetadata());
        dto.setPurchaseId(event.getPurchaseId());
        dto.setUserId(event.getUserId());
        dto.setPackageId(event.getPackageId());
        dto.setPackageCode(event.getPackageCode());
        dto.setBillingCycleNumber(event.getBillingCycleNumber());
        dto.setBankInstallmentCount(event.getBankInstallmentCount());
        dto.setSubscriptionId(event.getSubscriptionId());
        dto.setPeriodStart(event.getPeriodStart());
        dto.setPeriodEnd(event.getPeriodEnd());
        dto.setAmount(event.getAmount());
        dto.setCurrency(event.getCurrency());
        dto.setErrorCode(event.getErrorCode());
        dto.setFailureReason(event.getFailureReason());
        PaymentStyle style = event.getPaymentStyle();
        dto.setPaymentStyle(style == null ? null : style.name());
        return dto;
    }
}
