package com.ael.algoryqrservice.qrservice.integration.service.yemeksepeti;

import com.ael.algoryqrservice.qrservice.exception.BadRequestException;
import com.ael.algoryqrservice.qrservice.exception.NotFoundException;
import com.ael.algoryqrservice.qrservice.integration.yemeksepeti.crypto.YemekSepetiCredentialEncryptor;
import com.ael.algoryqrservice.qrservice.integration.yemeksepeti.model.YemekSepetiConnection;
import com.ael.algoryqrservice.qrservice.integration.yemeksepeti.model.YemekSepetiConnectionStatus;
import com.ael.algoryqrservice.qrservice.integration.yemeksepeti.model.dto.YemekSepetiDtos;
import com.ael.algoryqrservice.qrservice.integration.yemeksepeti.repository.YemekSepetiConnectionRepository;
import com.ael.algoryqrservice.qrservice.repository.BranchRepository;
import com.ael.algoryqrservice.qrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class YemekSepetiConnectionService {

    private final YemekSepetiConnectionRepository connectionRepository;
    private final YemekSepetiCredentialEncryptor encryptor;
    private final SecurityUtils securityUtils;
    private final BranchRepository branchRepository;

    @Transactional(readOnly = true)
    public List<YemekSepetiDtos.ConnectionResponse> listMine() {
        Long merchantId = securityUtils.getCurrentMerchantId();
        return connectionRepository.findByMerchantIdOrderByUpdatedAtDesc(merchantId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public YemekSepetiDtos.ConnectionResponse getMine(Long branchId) {
        return toResponse(requireOwnedConnection(branchId));
    }

    /** Connects [branchId]'s own account; without a branch, the account-wide connection (see [findForBranch]). */
    @Transactional
    public YemekSepetiDtos.ConnectionResponse upsert(YemekSepetiDtos.UpsertConnectionRequest request, Long branchId) {
        Long merchantId = securityUtils.getCurrentMerchantId();
        requireOwnedBranch(branchId, merchantId);
        YemekSepetiConnection connection = findForBranch(merchantId, branchId)
                .orElseGet(() -> YemekSepetiConnection.builder()
                        .merchantId(merchantId)
                        .branchId(branchId)
                        .status(YemekSepetiConnectionStatus.DISCONNECTED)
                        .build());
        connection.setChainId(request.getChainId().trim());
        if (hasText(request.getVendorId())) {
            connection.setVendorId(request.getVendorId().trim());
        }
        if (hasText(request.getClientId())) {
            connection.setClientIdEncrypted(encryptor.encrypt(request.getClientId().trim()));
        }
        if (hasText(request.getClientSecret())) {
            connection.setClientSecretEncrypted(encryptor.encrypt(request.getClientSecret().trim()));
        }
        if (hasText(request.getWebhookSecret())) {
            connection.setWebhookSecretEncrypted(encryptor.encrypt(request.getWebhookSecret().trim()));
        }
        if (connection.getClientIdEncrypted() == null
                || connection.getClientSecretEncrypted() == null
                || connection.getWebhookSecretEncrypted() == null) {
            throw new BadRequestException("Client ID, secret ve webhook secret zorunludur");
        }
        if (hasText(connection.getVendorId())) {
            connection.setStatus(YemekSepetiConnectionStatus.CONNECTED);
        } else {
            connection.setStatus(YemekSepetiConnectionStatus.PENDING_RESTAURANT);
        }
        connection.setLastError(null);
        return toResponse(connectionRepository.save(connection));
    }

    @Transactional
    public YemekSepetiDtos.ConnectionResponse disconnect(Long branchId) {
        YemekSepetiConnection connection = requireOwnedConnection(branchId);
        connection.setStatus(YemekSepetiConnectionStatus.DISCONNECTED);
        connection.setLastError(null);
        return toResponse(connectionRepository.save(connection));
    }

    /** Moves the account-wide connection to [branchId] (null: every branch); a branch keeps one connection. */
    @Transactional
    public YemekSepetiDtos.ConnectionResponse assignBranch(Long branchId) {
        YemekSepetiConnection connection = requireOwnedConnection(null);
        requireOwnedBranch(branchId, connection.getMerchantId());
        connectionRepository.findByMerchantIdAndBranchId(connection.getMerchantId(), branchId)
                .filter(other -> !other.getId().equals(connection.getId()))
                .ifPresent(other -> {
                    throw new BadRequestException("Bu şubenin zaten bir Yemeksepeti bağlantısı var");
                });
        connection.setBranchId(branchId);
        return toResponse(connectionRepository.save(connection));
    }

    @Transactional(readOnly = true)
    public YemekSepetiConnection requireOwnedConnection(Long branchId) {
        Long merchantId = securityUtils.getCurrentMerchantId();
        return findForBranch(merchantId, branchId)
                .orElseThrow(() -> new NotFoundException("Yemeksepeti bağlantısı bulunamadı"));
    }

    /** The account-wide connection, for callers that predate per-branch connections. */
    @Transactional(readOnly = true)
    public YemekSepetiConnection requireConnected() {
        return requireConnected(null);
    }

    @Transactional(readOnly = true)
    public YemekSepetiConnection requireConnected(Long branchId) {
        return requireLive(requireOwnedConnection(branchId));
    }

    /** The current merchant's connection an order belongs to, whichever branch it serves. */
    @Transactional(readOnly = true)
    public YemekSepetiConnection requireConnectedById(Long connectionId) {
        return requireConnectedForUser(securityUtils.getCurrentMerchantId(), connectionId);
    }

    @Transactional(readOnly = true)
    public YemekSepetiConnection requireConnectedForUser(Long merchantId, Long connectionId) {
        YemekSepetiConnection connection = connectionRepository.findById(connectionId)
                .filter(found -> found.getMerchantId().equals(merchantId))
                .orElseThrow(() -> new NotFoundException("Yemeksepeti bağlantısı bulunamadı"));
        return requireLive(connection);
    }

    /** Every connection of the merchant that can receive orders, one per branch. */
    @Transactional(readOnly = true)
    public List<YemekSepetiConnection> findConnectedForMerchant(Long merchantId) {
        if (merchantId == null) {
            return List.of();
        }
        return connectionRepository.findByMerchantIdOrderByUpdatedAtDesc(merchantId).stream()
                .filter(connection -> connection.getStatus() == YemekSepetiConnectionStatus.CONNECTED
                        && hasText(connection.getVendorId()))
                .toList();
    }

    /**
     * The connection of [branchId]. Without a branch (web panel, older clients): the account-wide connection,
     * or the most recently updated one when the merchant only has per-branch connections.
     */
    private java.util.Optional<YemekSepetiConnection> findForBranch(Long merchantId, Long branchId) {
        if (branchId != null) {
            return connectionRepository.findByMerchantIdAndBranchId(merchantId, branchId);
        }
        List<YemekSepetiConnection> all = connectionRepository.findByMerchantIdOrderByUpdatedAtDesc(merchantId);
        return all.stream().filter(connection -> connection.getBranchId() == null).findFirst()
                .or(() -> all.stream().findFirst());
    }

    private void requireOwnedBranch(Long branchId, Long merchantId) {
        if (branchId != null) {
            branchRepository.findByIdAndMerchantIdAndDeletedFalse(branchId, merchantId)
                    .orElseThrow(() -> new NotFoundException("Şube bulunamadı"));
        }
    }

    private YemekSepetiConnection requireLive(YemekSepetiConnection connection) {
        if (connection.getStatus() != YemekSepetiConnectionStatus.CONNECTED
                || !hasText(connection.getVendorId())) {
            throw new BadRequestException("Önce bir Yemeksepeti mağazası bağlayın");
        }
        return connection;
    }

    public YemekSepetiDtos.Credentials decrypt(YemekSepetiConnection connection) {
        return YemekSepetiDtos.Credentials.builder()
                .chainId(connection.getChainId())
                .vendorId(connection.getVendorId())
                .clientId(encryptor.decrypt(connection.getClientIdEncrypted()))
                .clientSecret(encryptor.decrypt(connection.getClientSecretEncrypted()))
                .webhookSecret(encryptor.decrypt(connection.getWebhookSecretEncrypted()))
                .build();
    }

    public YemekSepetiDtos.ConnectionResponse toResponse(YemekSepetiConnection connection) {
        String clientId = null;
        String webhookSecret = null;
        try {
            clientId = encryptor.decrypt(connection.getClientIdEncrypted());
        } catch (IllegalStateException ignored) {
            clientId = null;
        }
        try {
            webhookSecret = encryptor.decrypt(connection.getWebhookSecretEncrypted());
        } catch (IllegalStateException ignored) {
            webhookSecret = null;
        }
        return YemekSepetiDtos.ConnectionResponse.builder()
                .id(connection.getId())
                .chainId(connection.getChainId())
                .clientIdMasked(encryptor.mask(clientId))
                .webhookSecretMasked(encryptor.mask(webhookSecret))
                .vendorId(connection.getVendorId())
                .vendorName(connection.getVendorName())
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
