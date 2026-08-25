package com.ael.algoryqrservice.service.menuindex;

import com.ael.algoryqrservice.messaging.dto.MenuProductDocumentMessage;
import com.ael.algoryqrservice.model.MenuProduct;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationEventMenuProductIndexNotifierTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private MenuProductDocumentFactory documentFactory;

    @InjectMocks
    private ApplicationEventMenuProductIndexNotifier notifier;

    @Test
    void productChanged_whenProductIsActive_thenPublishUpsertedWithDocument() {
        MenuProduct product = product(11L, 5L, false);
        MenuProductDocumentMessage document = document(11L, 5L);
        when(documentFactory.create(product)).thenReturn(document);

        notifier.productChanged(product);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue())
                .isInstanceOf(MenuProductIndexEvents.Upserted.class)
                .extracting(event -> ((MenuProductIndexEvents.Upserted) event).documents())
                .isEqualTo(List.of(document));
    }

    @Test
    void productChanged_whenProductIsSoftDeleted_thenPublishRemoved() {
        notifier.productChanged(product(11L, 5L, true));

        verify(eventPublisher).publishEvent(new MenuProductIndexEvents.Removed(5L, 11L));
        verify(documentFactory, never()).create(any(MenuProduct.class));
    }

    @Test
    void productChanged_whenProductIsNull_thenPublishNothing() {
        notifier.productChanged(null);

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void productsChanged_whenAllProductsAreSoftDeleted_thenPublishNothing() {
        when(documentFactory.createAll(anyList())).thenReturn(List.of());

        notifier.productsChanged(List.of(product(1L, 5L, true)));

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void productRemoved_whenIdsAreMissing_thenPublishNothing() {
        notifier.productRemoved(null, 3L);
        notifier.productRemoved(5L, null);

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void menuRemoved_whenMenuIdGiven_thenPublishMenuPurged() {
        notifier.menuRemoved(5L);

        verify(eventPublisher).publishEvent(new MenuProductIndexEvents.MenuPurged(5L));
    }

    private static MenuProduct product(Long productId, Long menuId, boolean deleted) {
        MenuProduct product = MenuProduct.builder()
                .productId(productId)
                .menuId(menuId)
                .name("Mercimek Çorbası")
                .build();
        product.setDeleted(deleted);
        return product;
    }

    private static MenuProductDocumentMessage document(Long productId, Long menuId) {
        return new MenuProductDocumentMessage(
                productId, menuId, "Mercimek Çorbası", null,
                null, null, null, null,
                List.of(), List.of(), List.of(), List.of(),
                null, "TRY", true, false,
                null, null,
                null, null, null, null, null,
                null, null, null
        );
    }
}
