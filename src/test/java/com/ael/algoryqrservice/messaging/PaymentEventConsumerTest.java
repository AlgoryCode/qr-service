package com.ael.algoryqrservice.messaging;

import com.ael.algoryqrservice.exception.InvalidPaymentEventException;
import com.ael.algoryqrservice.messaging.payment.PaymentEventTypes;
import com.ael.algoryqrservice.messaging.payment.handler.PaymentEventHandlerRegistry;
import com.ael.algoryqrservice.messaging.payment.handler.PaymentFailedEventHandler;
import com.ael.algoryqrservice.model.dto.PaymentCompletedEventDto;
import com.ael.algoryqrservice.service.PurchaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentEventConsumerTest {

    @Mock
    private PurchaseService purchaseService;

    private PaymentEventHandlerRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new PaymentEventHandlerRegistry(List.of(new PaymentFailedEventHandler(purchaseService)));
    }

    @Test
    void dispatch_whenSubscriptionFailed_thenHandleFailed() {
        PaymentCompletedEventDto event = event(PaymentEventTypes.PAYMENT_SUBSCRIPTION_FAILED);

        registry.dispatch(event);

        verify(purchaseService).handlePaymentFailed(event);
    }

    @Test
    void dispatch_whenSubscriptionPastDue_thenHandlePastDue() {
        PaymentCompletedEventDto event = event(PaymentEventTypes.PAYMENT_SUBSCRIPTION_PAST_DUE);

        registry.dispatch(event);

        verify(purchaseService).handleSubscriptionPastDue(event);
    }

    @Test
    void dispatch_whenUnsupportedType_thenThrow() {
        PaymentCompletedEventDto event = event("payment.unknown");

        assertThatThrownBy(() -> registry.dispatch(event))
                .isInstanceOf(InvalidPaymentEventException.class)
                .hasMessageContaining("Unsupported");
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
