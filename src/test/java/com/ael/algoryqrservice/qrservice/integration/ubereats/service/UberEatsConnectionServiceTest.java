package com.ael.algoryqrservice.qrservice.integration.ubereats.service;

import com.ael.algoryqrservice.qrservice.integration.service.ubereats.UberEatsConnectionService;
import com.ael.algoryqrservice.qrservice.exception.NotFoundException;
import com.ael.algoryqrservice.qrservice.integration.ubereats.client.UberEatsClient;
import com.ael.algoryqrservice.qrservice.integration.ubereats.crypto.UberEatsCredentialEncryptor;
import com.ael.algoryqrservice.qrservice.integration.ubereats.mapper.UberEatsPayloadMapper;
import com.ael.algoryqrservice.qrservice.integration.ubereats.model.UberEatsConnection;
import com.ael.algoryqrservice.qrservice.integration.ubereats.model.UberEatsConnectionStatus;
import com.ael.algoryqrservice.qrservice.integration.ubereats.model.dto.UberEatsDtos;
import com.ael.algoryqrservice.qrservice.integration.ubereats.repository.UberEatsConnectionRepository;
import com.ael.algoryqrservice.qrservice.model.Branch;
import com.ael.algoryqrservice.qrservice.repository.BranchRepository;
import com.ael.algoryqrservice.qrservice.util.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
    @Mock
    private BranchRepository branchRepository;

    private UberEatsConnectionService connectionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        connectionService = new UberEatsConnectionService(
                connectionRepository,
                encryptor,
                UberEatsClient,
                new UberEatsPayloadMapper(),
                securityUtils,
                branchRepository
        );
    }

    @Test
    void upsert_whenNoBranch_thenStoresUserScopedConnection() {
        when(securityUtils.getCurrentMerchantId()).thenReturn(9L);
        when(connectionRepository.findByMerchantIdOrderByUpdatedAtDesc(9L)).thenReturn(List.of());
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

        UberEatsDtos.ConnectionResponse response = connectionService.upsert(request, null);

        ArgumentCaptor<UberEatsConnection> captor = ArgumentCaptor.forClass(UberEatsConnection.class);
        verify(connectionRepository).save(captor.capture());
        assertThat(captor.getValue().getMerchantId()).isEqualTo(9L);
        assertThat(captor.getValue().getStatus()).isEqualTo(UberEatsConnectionStatus.CONNECTED);
        assertThat(response.getRestaurantName()).isEqualTo("Mexican Doner");
        assertThat(response.getSellerId()).isEqualTo("6730477");
    }

    @Test
    void getMine_whenMissing_thenNotFound() {
        when(securityUtils.getCurrentMerchantId()).thenReturn(9L);
        when(connectionRepository.findByMerchantIdOrderByUpdatedAtDesc(9L)).thenReturn(List.of());
        assertThatThrownBy(() -> connectionService.getMine(null)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void disconnect_whenExists_thenMarksDisconnected() {
        when(securityUtils.getCurrentMerchantId()).thenReturn(9L);
        UberEatsConnection connection = UberEatsConnection.builder()
                .id(3L)
                .merchantId(9L)
                .sellerId("6730477")
                .apiKeyEncrypted("enc-key")
                .apiSecretEncrypted("enc-secret")
                .restaurantId("r-1")
                .restaurantName("Mexican Doner")
                .status(UberEatsConnectionStatus.CONNECTED)
                .build();
        when(connectionRepository.findByMerchantIdOrderByUpdatedAtDesc(9L)).thenReturn(List.of(connection));
        when(connectionRepository.save(any(UberEatsConnection.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(encryptor.decrypt("enc-key")).thenReturn("key");
        when(encryptor.mask("key")).thenReturn("k***");

        UberEatsDtos.ConnectionResponse response = connectionService.disconnect(null);

        assertThat(response.getStatus()).isEqualTo(UberEatsConnectionStatus.DISCONNECTED);
        assertThat(connection.getStatus()).isEqualTo(UberEatsConnectionStatus.DISCONNECTED);
    }

    @Test
    void upsert_whenBranchHasNoConnection_thenCreatesOneForThatBranch() {
        when(securityUtils.getCurrentMerchantId()).thenReturn(9L);
        when(branchRepository.findByIdAndMerchantIdAndDeletedFalse(5L, 9L)).thenReturn(Optional.of(new Branch()));
        when(connectionRepository.findByMerchantIdAndBranchId(9L, 5L)).thenReturn(Optional.empty());
        when(encryptor.encrypt("key")).thenReturn("enc-key");
        when(encryptor.encrypt("secret")).thenReturn("enc-secret");
        when(encryptor.decrypt("enc-key")).thenReturn("key");
        when(encryptor.decrypt("enc-secret")).thenReturn("secret");
        when(UberEatsClient.listRestaurants(any())).thenReturn(objectMapper.createObjectNode());
        when(connectionRepository.save(any(UberEatsConnection.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UberEatsDtos.UpsertConnectionRequest request = new UberEatsDtos.UpsertConnectionRequest();
        request.setSellerId("6730477");
        request.setApiKey("key");
        request.setApiSecret("secret");

        UberEatsDtos.ConnectionResponse response = connectionService.upsert(request, 5L);

        assertThat(response.getBranchId()).isEqualTo(5L);
        assertThat(response.getStatus()).isEqualTo(UberEatsConnectionStatus.PENDING_RESTAURANT);
        verify(connectionRepository, never()).findByMerchantIdOrderByUpdatedAtDesc(any());
    }
}
