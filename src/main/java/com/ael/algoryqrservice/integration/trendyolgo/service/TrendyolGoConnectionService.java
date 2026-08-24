package com.ael.algoryqrservice.integration.trendyolgo.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.NotFoundException;
import com.ael.algoryqrservice.integration.trendyolgo.client.TrendyolGoClient;
import com.ael.algoryqrservice.integration.trendyolgo.crypto.TrendyolGoCredentialEncryptor;
import com.ael.algoryqrservice.integration.trendyolgo.mapper.TrendyolGoPayloadMapper;
import com.ael.algoryqrservice.integration.trendyolgo.model.TrendyolGoConnection;
import com.ael.algoryqrservice.integration.trendyolgo.model.TrendyolGoConnectionStatus;
import com.ael.algoryqrservice.integration.trendyolgo.model.dto.TrendyolGoDtos;
import com.ael.algoryqrservice.integration.trendyolgo.repository.TrendyolGoConnectionRepository;
import com.ael.algoryqrservice.model.Branch;
import com.ael.algoryqrservice.repository.BranchRepository;
import com.ael.algoryqrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TrendyolGoConnectionService {

    private final TrendyolGoConnectionRepository connectionRepository;
    private final BranchRepository branchRepository;
    private final TrendyolGoCredentialEncryptor encryptor;
    private final TrendyolGoClient trendyolGoClient;
    private final TrendyolGoPayloadMapper payloadMapper;
    private final SecurityUtils securityUtils;

    @Transactional(readOnly = true)
    public List<TrendyolGoDtos.ConnectionResponse> listMine() {
        Long userId = securityUtils.getCurrentUserId();
        return connectionRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TrendyolGoDtos.ConnectionResponse getMine(Long branchId) {
        return toResponse(requireOwnedConnection(branchId));
    }

    @Transactional
    public TrendyolGoDtos.ConnectionResponse upsert(TrendyolGoDtos.UpsertConnectionRequest request) {
        Long userId = securityUtils.getCurrentUserId();
        Branch branch = requireOwnedBranch(request.getBranchId(), userId);
        TrendyolGoConnection connection = connectionRepository
                .findByUserIdAndBranchId(userId, branch.getId())
                .orElseGet(() -> TrendyolGoConnection.builder()
                        .userId(userId)
                        .branchId(branch.getId())
                        .status(TrendyolGoConnectionStatus.DISCONNECTED)
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
        TrendyolGoDtos.Credentials credentials = decrypt(connection);
        List<TrendyolGoDtos.RestaurantResponse> restaurants = payloadMapper.toRestaurants(
                trendyolGoClient.listRestaurants(credentials)
        );
        if (hasText(request.getRestaurantId())) {
            TrendyolGoDtos.RestaurantResponse selected = restaurants.stream()
                    .filter(restaurant -> request.getRestaurantId().equals(restaurant.getId()))
                    .findFirst()
                    .orElseThrow(() -> new BadRequestException("Seçilen restoran TGO hesabında bulunamadı"));
            connection.setRestaurantId(selected.getId());
            connection.setRestaurantName(selected.getName());
            connection.setStatus(TrendyolGoConnectionStatus.CONNECTED);
            connection.setLastError(null);
        } else {
            connection.setRestaurantId(null);
            connection.setRestaurantName(null);
            connection.setStatus(TrendyolGoConnectionStatus.PENDING_RESTAURANT);
            connection.setLastError(null);
        }
        return toResponse(connectionRepository.save(connection));
    }

    @Transactional
    public TrendyolGoDtos.ConnectionResponse disconnect(Long branchId) {
        TrendyolGoConnection connection = requireOwnedConnection(branchId);
        connection.setStatus(TrendyolGoConnectionStatus.DISCONNECTED);
        connection.setLastError(null);
        return toResponse(connectionRepository.save(connection));
    }

    @Transactional(readOnly = true)
    public List<TrendyolGoDtos.RestaurantResponse> listRestaurants(Long branchId) {
        TrendyolGoConnection connection = requireOwnedConnection(branchId);
        return payloadMapper.toRestaurants(trendyolGoClient.listRestaurants(decrypt(connection)));
    }

    @Transactional(readOnly = true)
    public TrendyolGoConnection requireOwnedConnection(Long branchId) {
        Long userId = securityUtils.getCurrentUserId();
        requireOwnedBranch(branchId, userId);
        return connectionRepository.findByUserIdAndBranchId(userId, branchId)
                .orElseThrow(() -> new NotFoundException("TGO bağlantısı bulunamadı"));
    }

    @Transactional(readOnly = true)
    public TrendyolGoConnection requireConnected(Long branchId) {
        TrendyolGoConnection connection = requireOwnedConnection(branchId);
        if (connection.getStatus() != TrendyolGoConnectionStatus.CONNECTED
                || !hasText(connection.getRestaurantId())) {
            throw new BadRequestException("Önce bir TGO restoranı bağlayın");
        }
        return connection;
    }

    public TrendyolGoDtos.Credentials decrypt(TrendyolGoConnection connection) {
        return TrendyolGoDtos.Credentials.builder()
                .sellerId(connection.getSellerId())
                .apiKey(encryptor.decrypt(connection.getApiKeyEncrypted()))
                .apiSecret(encryptor.decrypt(connection.getApiSecretEncrypted()))
                .restaurantId(connection.getRestaurantId())
                .build();
    }

    public TrendyolGoDtos.ConnectionResponse toResponse(TrendyolGoConnection connection) {
        String branchName = branchRepository.findById(connection.getBranchId())
                .map(Branch::getName)
                .orElse(null);
        String apiKey = null;
        try {
            apiKey = encryptor.decrypt(connection.getApiKeyEncrypted());
        } catch (IllegalStateException ignored) {
            apiKey = null;
        }
        return TrendyolGoDtos.ConnectionResponse.builder()
                .id(connection.getId())
                .branchId(connection.getBranchId())
                .branchName(branchName)
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

    private Branch requireOwnedBranch(Long branchId, Long userId) {
        return branchRepository.findByIdAndUserIdAndDeletedFalse(branchId, userId)
                .orElseThrow(() -> new NotFoundException("Şube bulunamadı"));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
