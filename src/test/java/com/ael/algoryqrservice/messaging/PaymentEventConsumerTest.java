package com.ael.algoryqrservice.messaging;

import com.ael.algoryqrservice.exception.InvalidPaymentEventException;
import com.ael.algoryqrservice.model.dto.PaymentCompletedEventDto;
import com.ael.algoryqrservice.service.PurchaseService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentEventConsumerTest {

    @Mock
    private PurchaseService purchaseService;

    @InjectMocks
    private PaymentEventConsumer consumer;

    @Test
    void onPaymentEvent_whenSubscriptionPaid_thenHandleSuccess() {
        PaymentCompletedEventDto event = event("payment.subscription.paid");

        consumer.onPaymentEvent(event);

        verify(purchaseService).handlePaymentSuccess(event);
    }

    @Test
    void onPaymentEvent_whenSubscriptionFailed_thenHandleFailed() {
        PaymentCompletedEventDto event = event("payment.subscription.failed");

        consumer.onPaymentEvent(event);

        verify(purchaseService).handlePaymentFailed(event);
    }

    @Test
    void onPaymentEvent_whenUnsupportedType_thenRejectWithoutRequeue() {
        PaymentCompletedEventDto event = event("payment.unknown");

        assertThatThrownBy(() -> consumer.onPaymentEvent(event))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .cause()
                .isInstanceOf(InvalidPaymentEventException.class);
    }

    private PaymentCompletedEventDto event(String type) {
        PaymentCompletedEventDto event = new PaymentCompletedEventDto();
        event.setEventId("e-1");
        event.setEventType(type);
        event.setSourceReferenceId("10");
        event.setConversationId("c-1");
        return event;
    }
}
