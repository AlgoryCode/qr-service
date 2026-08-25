package com.ael.algoryqrservice.service.menuindex;

import com.ael.algoryqrservice.config.MenuEventsRabbitProperties;
import com.ael.algoryqrservice.messaging.dto.MenuProductDocumentMessage;
import com.ael.algoryqrservice.messaging.dto.MenuProductIndexMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class MenuProductIndexPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private MenuEventsRabbitProperties properties;
    private MenuProductIndexPublisher publisher;

    @BeforeEach
    void setUp() {
        properties = new MenuEventsRabbitProperties();
        publisher = new MenuProductIndexPublisher(rabbitTemplate, properties);
    }

    @Test
    void onProductsUpserted_whenBatchGiven_thenSendOneMessagePerDocument() {
        publisher.onProductsUpserted(new MenuProductIndexEvents.Upserted(
                List.of(document(1L), document(2L))
        ));

        ArgumentCaptor<MenuProductIndexMessage> captor =
                ArgumentCaptor.forClass(MenuProductIndexMessage.class);
        verify(rabbitTemplate, times(2)).convertAndSend(
                eq("menu.events"),
                eq("menu.product.changed"),
                captor.capture()
        );
        assertThat(captor.getAllValues())
                .extracting(MenuProductIndexMessage::productId)
                .containsExactly(1L, 2L);
        assertThat(captor.getAllValues())
                .allMatch(message -> MenuProductIndexMessage.EVENT_UPSERTED.equals(message.eventType()));
    }

    @Test
    void onProductRemoved_whenEventReceived_thenSendDeleteWithoutDocument() {
        publisher.onProductRemoved(new MenuProductIndexEvents.Removed(5L, 9L));

        ArgumentCaptor<MenuProductIndexMessage> captor =
                ArgumentCaptor.forClass(MenuProductIndexMessage.class);
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo(MenuProductIndexMessage.EVENT_DELETED);
        assertThat(captor.getValue().document()).isNull();
        assertThat(captor.getValue().productId()).isEqualTo(9L);
    }

    @Test
    void onMenuPurged_whenEventReceived_thenSendPurgeWithoutProductId() {
        publisher.onMenuPurged(new MenuProductIndexEvents.MenuPurged(5L));

        ArgumentCaptor<MenuProductIndexMessage> captor =
                ArgumentCaptor.forClass(MenuProductIndexMessage.class);
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo(MenuProductIndexMessage.EVENT_MENU_PURGED);
        assertThat(captor.getValue().productId()).isNull();
        assertThat(captor.getValue().menuId()).isEqualTo(5L);
    }

    @Test
    void onProductRemoved_whenPublishingDisabled_thenSendNothing() {
        properties.setPublishEnabled(false);

        publisher.onProductRemoved(new MenuProductIndexEvents.Removed(5L, 9L));

        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void onProductRemoved_whenBrokerFails_thenSwallowSoCallerTransactionStaysCommitted() {
        doThrow(new AmqpException("broker down"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        assertThatCode(() -> publisher.onProductRemoved(new MenuProductIndexEvents.Removed(5L, 9L)))
                .doesNotThrowAnyException();
    }

    private static MenuProductDocumentMessage document(Long productId) {
        return new MenuProductDocumentMessage(
                productId, 5L, "Ürün " + productId, null,
                null, null, null, null,
                List.of(), List.of(), List.of(), List.of(),
                null, "TRY", true, false,
                null, null,
                null, null, null, null, null,
                null, null, null
        );
    }
}
