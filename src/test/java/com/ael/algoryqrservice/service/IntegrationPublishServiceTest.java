package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogProducts;
import com.ael.algoryqrservice.integration.ubereats.client.UberEatsClient;
import com.ael.algoryqrservice.integration.ubereats.client.UberEatsClientException;
import com.ael.algoryqrservice.integration.ubereats.mapper.UberEatsPayloadMapper;
import com.ael.algoryqrservice.integration.ubereats.model.UberEatsConnection;
import com.ael.algoryqrservice.integration.ubereats.model.UberEatsConnectionStatus;
import com.ael.algoryqrservice.integration.ubereats.model.dto.UberEatsDtos;
import com.ael.algoryqrservice.integration.ubereats.service.UberEatsConnectionService;
import com.ael.algoryqrservice.model.IntegrationPendingProduct;
import com.ael.algoryqrservice.model.enums.IntegrationJobStatus;
import com.ael.algoryqrservice.model.enums.IntegrationPublishTarget;
import com.ael.algoryqrservice.repository.IntegrationPendingProductRepository;
import com.ael.algoryqrservice.repository.MenuProductRepository;
import com.ael.algoryqrservice.repository.MenuSubCategoryRepository;
import com.ael.algoryqrservice.service.entitlement.FeatureUsageSyncRegistry;
import com.ael.algoryqrservice.service.menuindex.MenuProductIndexNotifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntegrationPublishServiceTest {

    @Mock
    private IntegrationPendingProductRepository pendingProductRepository;
    @Mock
    private MenuProductRepository menuProductRepository;
    @Mock
    private MenuSubCategoryRepository menuSubCategoryRepository;
    @Mock
    private EntitlementService entitlementService;
    @Mock
    private FeatureUsageSyncRegistry usageSyncRegistry;
    @Mock
    private MenuProductIndexNotifier menuProductIndexNotifier;
    @Mock
    private UberEatsConnectionService uberEatsConnectionService;
    @Mock
    private UberEatsClient uberEatsClient;

    private IntegrationPublishService publishService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        publishService = new IntegrationPublishService(
                pendingProductRepository,
                menuProductRepository,
                menuSubCategoryRepository,
                entitlementService,
                usageSyncRegistry,
                menuProductIndexNotifier,
                uberEatsConnectionService,
                uberEatsClient,
                new UberEatsPayloadMapper()
        );
    }

    @Test
    void publish_whenUberTarget_thenCallsUpsertClient() {
        UUID pendingId = UUID.randomUUID();
        ObjectNode data = objectMapper.createObjectNode();
        data.put("name", "Lahmacun");
        data.put("price", 90);
        data.put("currency", "TRY");
        data.put("available", true);
        IntegrationPendingProduct pending = IntegrationPendingProduct.builder()
                .id(pendingId)
                .jobId(UUID.randomUUID())
                .tenantId(7L)
                .menuId(3L)
                .source("INTERNAL")
                .sourceProductId("55")
                .productData(data)
                .approvalStatus(IntegrationJobStatus.APPROVED)
                .publishTargets(new LinkedHashSet<>(Set.of(IntegrationPublishTarget.UBEREATS)))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        when(pendingProductRepository.findById(pendingId)).thenReturn(Optional.of(pending));
        when(uberEatsConnectionService.requireConnectedForUser(7L)).thenReturn(
                UberEatsConnection.builder()
                        .userId(7L)
                        .restaurantId("store-1")
                        .status(UberEatsConnectionStatus.CONNECTED)
                        .build()
        );
        UberEatsDtos.Credentials credentials = UberEatsDtos.Credentials.builder()
                .sellerId("s1")
                .apiKey("k")
                .apiSecret("sec")
                .restaurantId("store-1")
                .build();
        when(uberEatsConnectionService.decrypt(any())).thenReturn(credentials);
        when(pendingProductRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        publishService.publish(pendingId);

        verify(uberEatsClient).upsertMenuProduct(eq(credentials), any());
        ArgumentCaptor<IntegrationPendingProduct> captor = ArgumentCaptor.forClass(IntegrationPendingProduct.class);
        verify(pendingProductRepository).save(captor.capture());
        assertThat(captor.getValue().getApprovalStatus()).isEqualTo(IntegrationJobStatus.PUBLISHED);
        assertThat(captor.getValue().getErrorMessage()).isNull();
        verify(usageSyncRegistry, never()).synchronize(any(), eq(CatalogProducts.MENU_PRODUCT));
    }

    @Test
    void publish_whenUberUpsertFails_thenMarksFailed() {
        UUID pendingId = UUID.randomUUID();
        ObjectNode data = objectMapper.createObjectNode();
        data.put("name", "Lahmacun");
        IntegrationPendingProduct pending = IntegrationPendingProduct.builder()
                .id(pendingId)
                .jobId(UUID.randomUUID())
                .tenantId(7L)
                .menuId(3L)
                .source("INTERNAL")
                .sourceProductId("55")
                .productData(data)
                .approvalStatus(IntegrationJobStatus.APPROVED)
                .publishTargets(new LinkedHashSet<>(Set.of(IntegrationPublishTarget.UBEREATS)))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        when(pendingProductRepository.findById(pendingId)).thenReturn(Optional.of(pending));
        when(uberEatsConnectionService.requireConnectedForUser(7L)).thenReturn(
                UberEatsConnection.builder()
                        .userId(7L)
                        .restaurantId("store-1")
                        .status(UberEatsConnectionStatus.CONNECTED)
                        .build()
        );
        when(uberEatsConnectionService.decrypt(any())).thenReturn(
                UberEatsDtos.Credentials.builder()
                        .sellerId("s1")
                        .apiKey("k")
                        .apiSecret("sec")
                        .restaurantId("store-1")
                        .build()
        );
        when(uberEatsClient.upsertMenuProduct(any(), any())).thenThrow(
                new UberEatsClientException("Uber Eats isteği başarısız oldu (HTTP 400)", null, 400)
        );
        when(pendingProductRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        publishService.publish(pendingId);

        ArgumentCaptor<IntegrationPendingProduct> captor = ArgumentCaptor.forClass(IntegrationPendingProduct.class);
        verify(pendingProductRepository).save(captor.capture());
        assertThat(captor.getValue().getApprovalStatus()).isEqualTo(IntegrationJobStatus.FAILED);
        assertThat(captor.getValue().getErrorMessage()).contains("HTTP 400");
    }
}
