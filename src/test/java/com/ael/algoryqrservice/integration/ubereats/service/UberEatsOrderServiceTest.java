package com.ael.algoryqrservice.integration.ubereats.service;

import com.ael.algoryqrservice.exception.UnauthorizedException;
import com.ael.algoryqrservice.integration.ubereats.client.UberEatsClient;
import com.ael.algoryqrservice.integration.ubereats.config.UberEatsProperties;
import com.ael.algoryqrservice.integration.ubereats.mapper.UberEatsPayloadMapper;
import com.ael.algoryqrservice.integration.ubereats.model.UberEatsConnection;
import com.ael.algoryqrservice.integration.ubereats.model.UberEatsOrder;
import com.ael.algoryqrservice.integration.ubereats.repository.UberEatsConnectionRepository;
import com.ael.algoryqrservice.integration.ubereats.repository.UberEatsOrderRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UberEatsOrderServiceTest {

    @Mock
    private UberEatsConnectionService connectionService;
    @Mock
    private UberEatsConnectionRepository connectionRepository;
    @Mock
    private UberEatsOrderRepository orderRepository;
    @Mock
    private UberEatsClient UberEatsClient;

    private UberEatsOrderService orderService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicLong ids = new AtomicLong(1);

    @BeforeEach
    void setUp() {
        UberEatsProperties properties = new UberEatsProperties();
        properties.setWebhookApiKey("hook-secret");
        properties.setPollEnabled(false);
        orderService = new UberEatsOrderService(
                connectionService,
                connectionRepository,
                orderRepository,
                UberEatsClient,
                new UberEatsPayloadMapper(),
                properties,
                objectMapper
        );
        org.mockito.Mockito.lenient().when(orderRepository.save(any(UberEatsOrder.class))).thenAnswer(invocation -> {
            UberEatsOrder order = invocation.getArgument(0);
            if (order.getId() == null) {
                order.setId(ids.getAndIncrement());
            }
            return order;
        });
    }

    @Test
    void ingestWebhook_whenSameOrderTwice_thenSingleUpsert() throws Exception {
        UberEatsConnection connection = connection(7L, "r-1");
        when(connectionRepository.findByRestaurantId("r-1")).thenReturn(List.of(connection));
        when(orderRepository.findByConnectionIdAndExternalOrderId(anyLong(), anyString()))
                .thenReturn(Optional.empty())
                .thenAnswer(invocation -> Optional.of(savedOrder(connection.getId(), invocation.getArgument(1))));
        JsonNode payload = objectMapper.readTree("""
                { "id": "ord-1", "restaurantId": "r-1", "packageStatus": "Created", "totalPrice": 90 }
                """);

        orderService.ingestWebhook("hook-secret", payload);
        orderService.ingestWebhook("hook-secret", payload);

        ArgumentCaptor<UberEatsOrder> captor = ArgumentCaptor.forClass(UberEatsOrder.class);
        verify(orderRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).allMatch(order -> "ord-1".equals(order.getExternalOrderId()));
        assertThat(captor.getAllValues()).allMatch(order -> connection.getId().equals(order.getConnectionId()));
    }

    @Test
    void ingestWebhook_whenWrongKey_thenUnauthorized() throws Exception {
        JsonNode payload = objectMapper.readTree("{ \"id\": \"ord-1\" }");
        assertThatThrownBy(() -> orderService.ingestWebhook("wrong", payload))
                .isInstanceOf(UnauthorizedException.class);
        verify(orderRepository, never()).save(any());
    }

    private UberEatsConnection connection(Long id, String restaurantId) {
        UberEatsConnection connection = new UberEatsConnection();
        connection.setId(id);
        connection.setRestaurantId(restaurantId);
        connection.setSellerId("seller-1");
        return connection;
    }

    private UberEatsOrder savedOrder(Long connectionId, String externalId) {
        return UberEatsOrder.builder()
                .id(1L)
                .connectionId(connectionId)
                .externalOrderId(externalId)
                .build();
    }
}
