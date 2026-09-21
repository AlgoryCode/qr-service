package com.ael.algoryqrservice.integration.yemeksepeti.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.NotFoundException;
import com.ael.algoryqrservice.integration.yemeksepeti.crypto.YemekSepetiCredentialEncryptor;
import com.ael.algoryqrservice.integration.yemeksepeti.model.YemekSepetiConnection;
import com.ael.algoryqrservice.integration.yemeksepeti.model.YemekSepetiConnectionStatus;
import com.ael.algoryqrservice.integration.yemeksepeti.model.dto.YemekSepetiDtos;
import com.ael.algoryqrservice.integration.yemeksepeti.repository.YemekSepetiConnectionRepository;
import com.ael.algoryqrservice.util.SecurityUtils;
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

    @Transactional(readOnly = true)
    public List<YemekSepetiDtos.ConnectionResponse> listMine() {
        Long userId = securityUtils.getCurrentUserId();
        return connectionRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public YemekSepetiDtos.ConnectionResponse getMine() {
        return toResponse(requireOwnedConnection());
    }

    @Transactional
    public YemekSepetiDtos.ConnectionResponse upsert(YemekSepetiDtos.UpsertConnectionRequest request) {
        Long userId = securityUtils.getCurrentUserId();
        YemekSepetiConnection connection = connectionRepository
                .findByUserId(userId)
                .orElseGet(() -> YemekSepetiConnection.builder()
                        .userId(userId)
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
    public YemekSepetiDtos.ConnectionResponse disconnect() {
        YemekSepetiConnection connection = requireOwnedConnection();
        connection.setStatus(YemekSepetiConnectionStatus.DISCONNECTED);
        connection.setLastError(null);
        return toResponse(connectionRepository.save(connection));
    }

    @Transactional(readOnly = true)
    public YemekSepetiConnection requireOwnedConnection() {
        Long userId = securityUtils.getCurrentUserId();
        return connectionRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Yemeksepeti bağlantısı bulunamadı"));
    }

    @Transactional(readOnly = true)
    public YemekSepetiConnection requireConnected() {
        YemekSepetiConnection connection = requireOwnedConnection();
        if (connection.getStatus() != YemekSepetiConnectionStatus.CONNECTED
                || !hasText(connection.getVendorId())) {
            throw new BadRequestException("Önce bir Yemeksepeti mağazası bağlayın");
        }
        return connection;
    }

    @Transactional(readOnly = true)
    public YemekSepetiConnection requireConnectedForUser(Long userId) {
        YemekSepetiConnection connection = connectionRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Yemeksepeti bağlantısı bulunamadı"));
        if (connection.getStatus() != YemekSepetiConnectionStatus.CONNECTED
                || !hasText(connection.getVendorId())) {
            throw new BadRequestException("Önce bir Yemeksepeti mağazası bağlayın");
        }
        return connection;
    }

    @Transactional(readOnly = true)
    public YemekSepetiConnection findByUserId(Long userId) {
        return connectionRepository.findByUserId(userId).orElse(null);
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
