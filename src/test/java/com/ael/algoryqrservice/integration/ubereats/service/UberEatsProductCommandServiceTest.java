package com.ael.algoryqrservice.integration.ubereats.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.integration.ubereats.client.UberEatsClient;
import com.ael.algoryqrservice.integration.ubereats.mapper.UberEatsPayloadMapper;
import com.ael.algoryqrservice.integration.ubereats.model.UberEatsConnection;
import com.ael.algoryqrservice.integration.ubereats.model.dto.UberEatsDtos;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UberEatsProductCommandServiceTest {

    @Mock
    private UberEatsConnectionService connectionService;
    @Mock
    private UberEatsClient uberEatsClient;

    private UberEatsProductCommandService productCommandService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        productCommandService = new UberEatsProductCommandService(
                connectionService,
                uberEatsClient,
                new UberEatsPayloadMapper()
        );
    }

    @Test
    void create_whenNameBlank_thenThrow() {
        UberEatsDtos.CreateProductRequest request = UberEatsDtos.CreateProductRequest.builder()
                .name("  ")
                .categoryName("Burger")
                .build();

        assertThatThrownBy(() -> productCommandService.create(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("name zorunludur");
        verify(uberEatsClient, never()).upsertMenuProduct(any(), any());
    }

    @Test
    void create_whenNotConnected_thenThrow() {
        when(connectionService.requireConnected())
                .thenThrow(new BadRequestException("Önce bir Uber Eats restoranı bağlayın"));
        UberEatsDtos.CreateProductRequest request = UberEatsDtos.CreateProductRequest.builder()
                .name("Burger")
                .categoryName("Burger")
                .build();

        assertThatThrownBy(() -> productCommandService.create(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Önce bir Uber Eats restoranı bağlayın");
        verify(uberEatsClient, never()).upsertMenuProduct(any(), any());
    }

    @Test
    void create_whenValid_thenUpsertMenuProduct() throws Exception {
        UberEatsConnection connection = new UberEatsConnection();
        connection.setId(3L);
        UberEatsDtos.Credentials credentials = UberEatsDtos.Credentials.builder()
                .sellerId("seller-1")
                .apiKey("key")
                .apiSecret("secret")
                .restaurantId("r-1")
                .build();
        when(connectionService.requireConnected()).thenReturn(connection);
        when(connectionService.decrypt(connection)).thenReturn(credentials);
        when(uberEatsClient.upsertMenuProduct(eq(credentials), any()))
                .thenReturn(objectMapper.readTree("""
                        { "id": "p-9", "name": "Cheeseburger", "price": 220, "categoryName": "Burger" }
                        """));
        UberEatsDtos.CreateProductRequest request = UberEatsDtos.CreateProductRequest.builder()
                .name("Cheeseburger")
                .price(new BigDecimal("220"))
                .categoryName("Burger")
                .available(true)
                .build();

        UberEatsDtos.ProductResponse created = productCommandService.create(request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(uberEatsClient).upsertMenuProduct(eq(credentials), captor.capture());
        assertThat(captor.getValue().get("name")).isEqualTo("Cheeseburger");
        assertThat(captor.getValue().get("categoryName")).isEqualTo("Burger");
        assertThat(created.getId()).isEqualTo("p-9");
        assertThat(created.getName()).isEqualTo("Cheeseburger");
    }
}
