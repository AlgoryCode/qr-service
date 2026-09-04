package com.ael.algoryqrservice.integration.ubereats.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.NotFoundException;
import com.ael.algoryqrservice.integration.ubereats.client.UberEatsClient;
import com.ael.algoryqrservice.integration.ubereats.crypto.UberEatsCredentialEncryptor;
import com.ael.algoryqrservice.integration.ubereats.mapper.UberEatsPayloadMapper;
import com.ael.algoryqrservice.integration.ubereats.model.UberEatsConnection;
import com.ael.algoryqrservice.integration.ubereats.model.UberEatsConnectionStatus;
import com.ael.algoryqrservice.integration.ubereats.model.dto.UberEatsDtos;
import com.ael.algoryqrservice.integration.ubereats.repository.UberEatsConnectionRepository;
import com.ael.algoryqrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UberEatsConnectionService {

    private final UberEatsConnectionRepository connectionRepository;
    private final UberEatsCredentialEncryptor encryptor;
    private final UberEatsClient uberEatsClient;
    private final UberEatsPayloadMapper payloadMapper;
    private final SecurityUtils securityUtils;

    @Transactional(readOnly = true)
    public List<UberEatsDtos.ConnectionResponse> listMine() {
        Long userId = securityUtils.getCurrentUserId();
        return connectionRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public UberEatsDtos.ConnectionResponse getMine() {
        return toResponse(requireOwnedConnection());
    }

    @Transactional
    public UberEatsDtos.ConnectionResponse upsert(UberEatsDtos.UpsertConnectionRequest request) {
        Long userId = securityUtils.getCurrentUserId();
        UberEatsConnection connection = connectionRepository
                .findByUserId(userId)
                .orElseGet(() -> UberEatsConnection.builder()
                        .userId(userId)
                        .status(UberEatsConnectionStatus.DISCONNECTED)
                        .build());
        connection.setSellerId(request.getSellerId().trim());
        if (hasText(request.getApiKey())) {
            connection.setApiKeyEncrypted(encryptor.encrypt(request.getApiKey().trim()));
        }
        if (hasText(request.getApiSecret())) {
            connection.setApiSecretEncrypted(encryptor.encrypt(request.getApiSecret().trim()));
        }
        if (connection.getApiKeyEncrypted() == null || connection.getApiSecretEncrypted() == null) {
            throw new BadRequestException("API key ve secret zorunludur");
        }
        UberEatsDtos.Credentials credentials = decrypt(connection);
        List<UberEatsDtos.RestaurantResponse> restaurants = payloadMapper.toRestaurants(
                uberEatsClient.listRestaurants(credentials)
        );
        if (hasText(request.getRestaurantId())) {
            UberEatsDtos.RestaurantResponse selected = restaurants.stream()
                    .filter(restaurant -> request.getRestaurantId().equals(restaurant.getId()))
                    .findFirst()
                    .orElseThrow(() -> new BadRequestException("Seçilen restoran Uber Eats hesabında bulunamadı"));
            connection.setRestaurantId(selected.getId());
            connection.setRestaurantName(selected.getName());
            connection.setStatus(UberEatsConnectionStatus.CONNECTED);
            connection.setLastError(null);
        } else {
            connection.setRestaurantId(null);
            connection.setRestaurantName(null);
            connection.setStatus(UberEatsConnectionStatus.PENDING_RESTAURANT);
            connection.setLastError(null);
        }
        return toResponse(connectionRepository.save(connection));
    }

    @Transactional
    public UberEatsDtos.ConnectionResponse disconnect() {
        UberEatsConnection connection = requireOwnedConnection();
        connection.setStatus(UberEatsConnectionStatus.DISCONNECTED);
        connection.setLastError(null);
        return toResponse(connectionRepository.save(connection));
    }

    @Transactional(readOnly = true)
    public List<UberEatsDtos.RestaurantResponse> listRestaurants() {
        UberEatsConnection connection = requireOwnedConnection();
        return payloadMapper.toRestaurants(uberEatsClient.listRestaurants(decrypt(connection)));
    }

    @Transactional(readOnly = true)
    public UberEatsConnection requireOwnedConnection() {
        Long userId = securityUtils.getCurrentUserId();
        return connectionRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Uber Eats bağlantısı bulunamadı"));
    }

    @Transactional(readOnly = true)
    public UberEatsConnection requireConnected() {
        UberEatsConnection connection = requireOwnedConnection();
        if (connection.getStatus() != UberEatsConnectionStatus.CONNECTED
                || !hasText(connection.getRestaurantId())) {
            throw new BadRequestException("Önce bir Uber Eats restoranı bağlayın");
        }
        return connection;
    }

    @Transactional(readOnly = true)
    public UberEatsConnection findByUserId(Long userId) {
        return connectionRepository.findByUserId(userId).orElse(null);
    }

    public UberEatsDtos.Credentials decrypt(UberEatsConnection connection) {
        return UberEatsDtos.Credentials.builder()
                .sellerId(connection.getSellerId())
                .apiKey(encryptor.decrypt(connection.getApiKeyEncrypted()))
                .apiSecret(encryptor.decrypt(connection.getApiSecretEncrypted()))
                .restaurantId(connection.getRestaurantId())
                .build();
    }

    public UberEatsDtos.ConnectionResponse toResponse(UberEatsConnection connection) {
        String apiKey = null;
        try {
            apiKey = encryptor.decrypt(connection.getApiKeyEncrypted());
        } catch (IllegalStateException ignored) {
            apiKey = null;
        }
        return UberEatsDtos.ConnectionResponse.builder()
                .id(connection.getId())
                .sellerId(connection.getSellerId())
                .apiKeyMasked(encryptor.mask(apiKey))
                .restaurantId(connection.getRestaurantId())
                .restaurantName(connection.getRestaurantName())
                .status(connection.getStatus())
                .lastError(connection.getLastError())
                .lastSyncedAt(connection.getLastSyncedAt())
                .updatedAt(connection.getUpdatedAt())
                .build();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
