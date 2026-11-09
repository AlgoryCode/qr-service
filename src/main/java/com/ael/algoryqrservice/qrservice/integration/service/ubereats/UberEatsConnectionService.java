package com.ael.algoryqrservice.qrservice.integration.service.ubereats;

import com.ael.algoryqrservice.qrservice.exception.BadRequestException;
import com.ael.algoryqrservice.qrservice.exception.NotFoundException;
import com.ael.algoryqrservice.qrservice.integration.ubereats.client.UberEatsClient;
import com.ael.algoryqrservice.qrservice.integration.ubereats.crypto.UberEatsCredentialEncryptor;
import com.ael.algoryqrservice.qrservice.integration.ubereats.mapper.UberEatsPayloadMapper;
import com.ael.algoryqrservice.qrservice.integration.ubereats.model.UberEatsConnection;
import com.ael.algoryqrservice.qrservice.integration.ubereats.model.UberEatsConnectionStatus;
import com.ael.algoryqrservice.qrservice.integration.ubereats.model.dto.UberEatsDtos;
import com.ael.algoryqrservice.qrservice.integration.ubereats.repository.UberEatsConnectionRepository;
import com.ael.algoryqrservice.qrservice.repository.BranchRepository;
import com.ael.algoryqrservice.qrservice.util.SecurityUtils;
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
    private final BranchRepository branchRepository;

    @Transactional(readOnly = true)
    public List<UberEatsDtos.ConnectionResponse> listMine() {
        Long merchantId = securityUtils.getCurrentMerchantId();
        return connectionRepository.findByMerchantIdOrderByUpdatedAtDesc(merchantId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public UberEatsDtos.ConnectionResponse getMine(Long branchId) {
        return toResponse(requireOwnedConnection(branchId));
    }

    /** Connects [branchId]'s own account; without a branch, the account-wide connection (see [findForBranch]). */
    @Transactional
    public UberEatsDtos.ConnectionResponse upsert(UberEatsDtos.UpsertConnectionRequest request, Long branchId) {
        Long merchantId = securityUtils.getCurrentMerchantId();
        requireOwnedBranch(branchId, merchantId);
        UberEatsConnection connection = findForBranch(merchantId, branchId)
                .orElseGet(() -> UberEatsConnection.builder()
                        .merchantId(merchantId)
                        .branchId(branchId)
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

    /** Moves the account-wide connection to [branchId] (null: every branch); a branch keeps one connection. */
    @Transactional
    public UberEatsDtos.ConnectionResponse assignBranch(Long branchId) {
        UberEatsConnection connection = requireOwnedConnection(null);
        requireOwnedBranch(branchId, connection.getMerchantId());
        connectionRepository.findByMerchantIdAndBranchId(connection.getMerchantId(), branchId)
                .filter(other -> !other.getId().equals(connection.getId()))
                .ifPresent(other -> {
                    throw new BadRequestException("Bu şubenin zaten bir Uber Eats bağlantısı var");
                });
        connection.setBranchId(branchId);
        return toResponse(connectionRepository.save(connection));
    }

    @Transactional
    public UberEatsDtos.ConnectionResponse disconnect(Long branchId) {
        UberEatsConnection connection = requireOwnedConnection(branchId);
        connection.setStatus(UberEatsConnectionStatus.DISCONNECTED);
        connection.setLastError(null);
        return toResponse(connectionRepository.save(connection));
    }

    @Transactional(readOnly = true)
    public List<UberEatsDtos.RestaurantResponse> listRestaurants(Long branchId) {
        UberEatsConnection connection = requireOwnedConnection(branchId);
        return payloadMapper.toRestaurants(uberEatsClient.listRestaurants(decrypt(connection)));
    }

    @Transactional(readOnly = true)
    public UberEatsConnection requireOwnedConnection(Long branchId) {
        Long merchantId = securityUtils.getCurrentMerchantId();
        return findForBranch(merchantId, branchId)
                .orElseThrow(() -> new NotFoundException("Uber Eats bağlantısı bulunamadı"));
    }

    /** The account-wide connection, for callers that predate per-branch connections. */
    @Transactional(readOnly = true)
    public UberEatsConnection requireConnected() {
        return requireConnected(null);
    }

    @Transactional(readOnly = true)
    public UberEatsConnection requireConnected(Long branchId) {
        return requireLive(requireOwnedConnection(branchId));
    }

    /** The current merchant's connection an order belongs to, whichever branch it serves. */
    @Transactional(readOnly = true)
    public UberEatsConnection requireConnectedById(Long connectionId) {
        return requireConnectedForUser(securityUtils.getCurrentMerchantId(), connectionId);
    }

    @Transactional(readOnly = true)
    public UberEatsConnection requireConnectedForUser(Long merchantId, Long connectionId) {
        UberEatsConnection connection = connectionRepository.findById(connectionId)
                .filter(found -> found.getMerchantId().equals(merchantId))
                .orElseThrow(() -> new NotFoundException("Uber Eats bağlantısı bulunamadı"));
        return requireLive(connection);
    }

    /**
     * The connection that serves [branchId]: the branch's own one, else the account-wide one (no branch). For
     * background jobs that run without a signed-in owner, such as publishing a menu's products.
     */
    @Transactional(readOnly = true)
    public UberEatsConnection requireConnectedForBranch(Long merchantId, Long branchId) {
        java.util.Optional<UberEatsConnection> own = branchId == null
                ? java.util.Optional.empty()
                : connectionRepository.findByMerchantIdAndBranchId(merchantId, branchId);
        UberEatsConnection connection = own
                .or(() -> connectionRepository.findByMerchantIdAndBranchId(merchantId, null))
                .orElseThrow(() -> new NotFoundException("Uber Eats bağlantısı bulunamadı"));
        return requireLive(connection);
    }

    /** Every connection of the merchant that can receive orders, one per branch. */
    @Transactional(readOnly = true)
    public List<UberEatsConnection> findConnectedForMerchant(Long merchantId) {
        if (merchantId == null) {
            return List.of();
        }
        return connectionRepository.findByMerchantIdOrderByUpdatedAtDesc(merchantId).stream()
                .filter(connection -> connection.getStatus() == UberEatsConnectionStatus.CONNECTED
                        && hasText(connection.getRestaurantId()))
                .toList();
    }

    /**
     * The connection of [branchId]. Without a branch (web panel, older clients): the account-wide connection,
     * or the most recently updated one when the merchant only has per-branch connections.
     */
    private java.util.Optional<UberEatsConnection> findForBranch(Long merchantId, Long branchId) {
        if (branchId != null) {
            return connectionRepository.findByMerchantIdAndBranchId(merchantId, branchId);
        }
        List<UberEatsConnection> all = connectionRepository.findByMerchantIdOrderByUpdatedAtDesc(merchantId);
        return all.stream().filter(connection -> connection.getBranchId() == null).findFirst()
                .or(() -> all.stream().findFirst());
    }

    private void requireOwnedBranch(Long branchId, Long merchantId) {
        if (branchId != null) {
            branchRepository.findByIdAndMerchantIdAndDeletedFalse(branchId, merchantId)
                    .orElseThrow(() -> new NotFoundException("Şube bulunamadı"));
        }
    }

    private UberEatsConnection requireLive(UberEatsConnection connection) {
        if (connection.getStatus() != UberEatsConnectionStatus.CONNECTED
                || !hasText(connection.getRestaurantId())) {
            throw new BadRequestException("Önce bir Uber Eats restoranı bağlayın");
        }
        return connection;
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
                .branchId(connection.getBranchId())
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
