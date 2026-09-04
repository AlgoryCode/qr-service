package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.integration.ubereats.client.UberEatsClient;
import com.ael.algoryqrservice.integration.ubereats.mapper.UberEatsPayloadMapper;
import com.ael.algoryqrservice.integration.ubereats.model.UberEatsConnection;
import com.ael.algoryqrservice.integration.ubereats.model.UberEatsConnectionStatus;
import com.ael.algoryqrservice.integration.ubereats.service.UberEatsConnectionService;
import com.ael.algoryqrservice.messaging.IntegrationMessagePublisher;
import com.ael.algoryqrservice.model.IntegrationJob;
import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.MenuCategory;
import com.ael.algoryqrservice.model.MenuProduct;
import com.ael.algoryqrservice.model.MenuSubCategory;
import com.ael.algoryqrservice.model.dto.IntegrationPendingProductDtos;
import com.ael.algoryqrservice.model.enums.IntegrationDirection;
import com.ael.algoryqrservice.model.enums.IntegrationJobStatus;
import com.ael.algoryqrservice.repository.IntegrationJobRepository;
import com.ael.algoryqrservice.repository.MenuCategoryRepository;
import com.ael.algoryqrservice.repository.MenuProductRepository;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.repository.MenuSubCategoryRepository;
import com.ael.algoryqrservice.util.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntegrationExportServiceTest {

    @Mock
    private MenuRepository menuRepository;
    @Mock
    private MenuProductRepository menuProductRepository;
    @Mock
    private MenuSubCategoryRepository menuSubCategoryRepository;
    @Mock
    private MenuCategoryRepository menuCategoryRepository;
    @Mock
    private IntegrationJobRepository jobRepository;
    @Mock
    private IntegrationMessagePublisher messagePublisher;
    @Mock
    private UberEatsConnectionService uberEatsConnectionService;
    @Mock
    private UberEatsClient uberEatsClient;
    @Mock
    private SecurityUtils securityUtils;

    private IntegrationExportService exportService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        exportService = new IntegrationExportService(
                menuRepository,
                menuProductRepository,
                menuSubCategoryRepository,
                menuCategoryRepository,
                jobRepository,
                messagePublisher,
                uberEatsConnectionService,
                uberEatsClient,
                new UberEatsPayloadMapper(),
                securityUtils,
                objectMapper
        );
    }

    @Test
    void exportToUberEats_whenMenuHasProducts_thenEnqueuesExportSnapshot() {
        Menu menu = Menu.builder()
                .menuId(10L)
                .userId(5L)
                .businessName("Cafe")
                .deleted(false)
                .build();
        when(securityUtils.getCurrentUserId()).thenReturn(5L);
        when(menuRepository.findById(10L)).thenReturn(Optional.of(menu));
        when(uberEatsConnectionService.requireConnected()).thenReturn(
                UberEatsConnection.builder()
                        .userId(5L)
                        .restaurantId("r-1")
                        .status(UberEatsConnectionStatus.CONNECTED)
                        .build()
        );

        MenuCategory category = MenuCategory.builder().id(1L).menuId(10L).name("Ana").deleted(false).build();
        MenuSubCategory sub = MenuSubCategory.builder()
                .id(2L)
                .menuId(10L)
                .menuCategoryId(1L)
                .name("Burger")
                .deleted(false)
                .build();
        MenuProduct product = MenuProduct.builder()
                .productId(99L)
                .menuId(10L)
                .name("Cheeseburger")
                .description("Et")
                .price(new BigDecimal("120.00"))
                .currency("TRY")
                .subCategoryId(2L)
                .available(true)
                .deleted(false)
                .build();
        when(menuProductRepository.findByMenuIdAndDeletedFalseOrderBySortOrderAscProductIdAsc(10L))
                .thenReturn(List.of(product));
        when(menuSubCategoryRepository.findByMenuIdAndDeletedFalseOrderBySortOrderAscIdAsc(10L))
                .thenReturn(List.of(sub));
        when(menuCategoryRepository.findByMenuIdAndDeletedFalseOrderBySortOrderAscIdAsc(10L))
                .thenReturn(List.of(category));
        when(jobRepository.save(any(IntegrationJob.class))).thenAnswer(invocation -> invocation.getArgument(0));

        IntegrationPendingProductDtos.JobAccepted accepted = exportService.exportToUberEats(10L);

        assertThat(accepted.getDirection()).isEqualTo(IntegrationDirection.EXPORT_TO_UBEREATS);
        assertThat(accepted.getStatus()).isEqualTo(IntegrationJobStatus.QUEUED);
        assertThat(accepted.getJobId()).isNotNull();

        ArgumentCaptor<IntegrationJob> jobCaptor = ArgumentCaptor.forClass(IntegrationJob.class);
        verify(jobRepository).save(jobCaptor.capture());
        verify(messagePublisher).publishAiRequested(any(IntegrationJob.class));

        IntegrationJob saved = jobCaptor.getValue();
        assertThat(saved.getDirection()).isEqualTo(IntegrationDirection.EXPORT_TO_UBEREATS);
        assertThat(saved.getExternalStoreId()).isEqualTo("r-1");
        ArrayNode products = (ArrayNode) saved.getSnapshot().get("products");
        assertThat(products).hasSize(1);
        assertThat(products.get(0).get("sourceProductId").asText()).isEqualTo("99");
        assertThat(products.get(0).get("internalProductId").asLong()).isEqualTo(99L);
        assertThat(products.get(0).get("name").asText()).isEqualTo("Cheeseburger");
        assertThat(products.get(0).get("category").asText()).isEqualTo("Ana");
        assertThat(products.get(0).get("subcategory").asText()).isEqualTo("Burger");
    }
}
