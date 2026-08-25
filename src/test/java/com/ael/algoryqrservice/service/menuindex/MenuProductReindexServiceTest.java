package com.ael.algoryqrservice.service.menuindex;

import com.ael.algoryqrservice.client.AiServiceClient;
import com.ael.algoryqrservice.client.dto.MenuProductReindexDtos;
import com.ael.algoryqrservice.config.AiServiceProperties;
import com.ael.algoryqrservice.messaging.dto.MenuProductDocumentMessage;
import com.ael.algoryqrservice.model.MenuProduct;
import com.ael.algoryqrservice.repository.MenuProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MenuProductReindexServiceTest {

    @Mock
    private MenuProductRepository menuProductRepository;
    @Mock
    private MenuProductDocumentFactory documentFactory;
    @Mock
    private AiServiceClient aiServiceClient;

    private AiServiceProperties properties;
    private MenuProductReindexService service;

    @BeforeEach
    void setUp() {
        properties = new AiServiceProperties();
        service = new MenuProductReindexService(
                menuProductRepository,
                documentFactory,
                aiServiceClient,
                properties
        );
    }

    @Test
    void reindexMenu_whenMenuHasNoProducts_thenSkipRemoteCall() {
        stubProducts(0);

        MenuProductReindexService.ReindexSummary summary = service.reindexMenu(5L);

        assertThat(summary.products()).isZero();
        verify(aiServiceClient, never()).reindex(anyList(), any(), any());
    }

    @Test
    void reindexMenu_whenProductsExceedBatchSize_thenPurgeOnlyOnLastBatchWithFullKeepList() {
        properties.setReindexBatchSize(2);
        stubProducts(5);
        when(aiServiceClient.reindex(anyList(), any(), any()))
                .thenReturn(new MenuProductReindexDtos.Response(2, 0, "text-embedding-3-small"));

        service.reindexMenu(5L);

        ArgumentCaptor<Long> purgeCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<List<Long>> keepCaptor = ArgumentCaptor.forClass(List.class);
        verify(aiServiceClient, times(3)).reindex(anyList(), purgeCaptor.capture(), keepCaptor.capture());

        assertThat(purgeCaptor.getAllValues()).containsExactly(null, null, 5L);
        assertThat(keepCaptor.getAllValues().getLast()).containsExactly(1L, 2L, 3L, 4L, 5L);
    }

    @Test
    void reindexMenu_whenAllBatchesSucceed_thenSummarizeIndexedAndPurgedCounts() {
        properties.setReindexBatchSize(10);
        stubProducts(3);
        when(aiServiceClient.reindex(anyList(), any(), any()))
                .thenReturn(new MenuProductReindexDtos.Response(3, 2, "text-embedding-3-small"));

        MenuProductReindexService.ReindexSummary summary = service.reindexMenu(5L);

        assertThat(summary).isEqualTo(new MenuProductReindexService.ReindexSummary(5L, 3, 3, 2));
    }

    private void stubProducts(int count) {
        List<MenuProduct> products = IntStream.rangeClosed(1, count)
                .<MenuProduct>mapToObj(index -> MenuProduct.builder()
                        .productId((long) index)
                        .menuId(5L)
                        .name("Ürün " + index)
                        .build())
                .toList();
        when(menuProductRepository.findByMenuIdAndDeletedFalseOrderBySortOrderAscProductIdAsc(5L))
                .thenReturn(products);
        when(documentFactory.createAll(products)).thenReturn(
                IntStream.rangeClosed(1, count).mapToObj(index -> document((long) index)).toList()
        );
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
