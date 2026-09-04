package com.ael.algoryqrservice.integration.ubereats.service;

import com.ael.algoryqrservice.exception.NotFoundException;
import com.ael.algoryqrservice.integration.ubereats.client.UberEatsClient;
import com.ael.algoryqrservice.integration.ubereats.crypto.UberEatsCredentialEncryptor;
import com.ael.algoryqrservice.integration.ubereats.mapper.UberEatsPayloadMapper;
import com.ael.algoryqrservice.integration.ubereats.model.UberEatsConnection;
import com.ael.algoryqrservice.integration.ubereats.model.UberEatsConnectionStatus;
import com.ael.algoryqrservice.integration.ubereats.model.dto.UberEatsDtos;
import com.ael.algoryqrservice.integration.ubereats.repository.UberEatsConnectionRepository;
import com.ael.algoryqrservice.util.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UberEatsConnectionServiceTest {

    @Mock
    private UberEatsConnectionRepository connectionRepository;
    @Mock
    private UberEatsCredentialEncryptor encryptor;
    @Mock
    private UberEatsClient UberEatsClient;
    @Mock
    private SecurityUtils securityUtils;

    private UberEatsConnectionService connectionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        connectionService = new UberEatsConnectionService(
                connectionRepository,
                encryptor,
                UberEatsClient,
                new UberEatsPayloadMapper(),
                securityUtils
        );
    }

    @Test
    void upsert_whenNoBranch_thenStoresUserScopedConnection() {
        when(securityUtils.getCurrentUserId()).thenReturn(9L);
        when(connectionRepository.findByUserId(9L)).thenReturn(Optional.empty());
        when(encryptor.encrypt("key")).thenReturn("enc-key");
        when(encryptor.encrypt("secret")).thenReturn("enc-secret");
        when(encryptor.decrypt("enc-key")).thenReturn("key");
        when(encryptor.decrypt("enc-secret")).thenReturn("secret");
        when(encryptor.mask("key")).thenReturn("k***");

        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode restaurants = root.putArray("restaurants");
        ObjectNode restaurant = restaurants.addObject();
        restaurant.put("id", "r-1");
        restaurant.put("name", "Mexican Doner");
        when(UberEatsClient.listRestaurants(any())).thenReturn(root);

        when(connectionRepository.save(any(UberEatsConnection.class))).thenAnswer(invocation -> {
            UberEatsConnection saved = invocation.getArgument(0);
            saved.setId(11L);
            return saved;
        });

        UberEatsDtos.UpsertConnectionRequest request = new UberEatsDtos.UpsertConnectionRequest();
        request.setSellerId("6730477");
        request.setApiKey("key");
        request.setApiSecret("secret");
        request.setRestaurantId("r-1");

        UberEatsDtos.ConnectionResponse response = connectionService.upsert(request);

        ArgumentCaptor<UberEatsConnection> captor = ArgumentCaptor.forClass(UberEatsConnection.class);
        verify(connectionRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(9L);
        assertThat(captor.getValue().getStatus()).isEqualTo(UberEatsConnectionStatus.CONNECTED);
        assertThat(response.getRestaurantName()).isEqualTo("Mexican Doner");
        assertThat(response.getSellerId()).isEqualTo("6730477");
    }

    @Test
    void getMine_whenMissing_thenNotFound() {
        when(securityUtils.getCurrentUserId()).thenReturn(9L);
        when(connectionRepository.findByUserId(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> connectionService.getMine()).isInstanceOf(NotFoundException.class);
    }

    @Test
    void disconnect_whenExists_thenMarksDisconnected() {
        when(securityUtils.getCurrentUserId()).thenReturn(9L);
        UberEatsConnection connection = UberEatsConnection.builder()
                .id(3L)
                .userId(9L)
                .sellerId("6730477")
                .apiKeyEncrypted("enc-key")
                .apiSecretEncrypted("enc-secret")
                .restaurantId("r-1")
                .restaurantName("Mexican Doner")
                .status(UberEatsConnectionStatus.CONNECTED)
                .build();
        when(connectionRepository.findByUserId(9L)).thenReturn(Optional.of(connection));
        when(connectionRepository.save(any(UberEatsConnection.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(encryptor.decrypt("enc-key")).thenReturn("key");
        when(encryptor.mask("key")).thenReturn("k***");

        UberEatsDtos.ConnectionResponse response = connectionService.disconnect();

        assertThat(response.getStatus()).isEqualTo(UberEatsConnectionStatus.DISCONNECTED);
        assertThat(connection.getStatus()).isEqualTo(UberEatsConnectionStatus.DISCONNECTED);
    }
}
