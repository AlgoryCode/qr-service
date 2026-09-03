package com.ael.algoryqrservice.integration.ubereats.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.NotFoundException;
import com.ael.algoryqrservice.integration.ubereats.client.UberEatsClient;
import com.ael.algoryqrservice.integration.ubereats.client.UberEatsClientException;
import com.ael.algoryqrservice.integration.ubereats.crypto.UberEatsCredentialEncryptor;
import com.ael.algoryqrservice.integration.ubereats.model.UberEatsConnection;
import com.ael.algoryqrservice.integration.ubereats.model.UberEatsConnectionStatus;
import com.ael.algoryqrservice.integration.ubereats.model.dto.UberEatsDtos;
import com.ael.algoryqrservice.integration.ubereats.repository.UberEatsConnectionRepository;
import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UberEatsConnectionService {

    private final UberEatsConnectionRepository connectionRepository;
    private final MenuRepository menuRepository;
    private final UberEatsCredentialEncryptor encryptor;
    private final UberEatsClient uberEatsClient;
    private final SecurityUtils securityUtils;

    @Transactional(readOnly = true)
    public List<UberEatsDtos.ConnectionResponse> listMine() {
        return connectionRepository.findByUserIdOrderByUpdatedAtDesc(securityUtils.getCurrentUserId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public UberEatsDtos.ConnectionResponse getMine(Long menuId) {
        return toResponse(requireOwnedConnection(menuId));
    }

    @Transactional
    public UberEatsDtos.ConnectionResponse upsert(UberEatsDtos.UpsertConnectionRequest request) {
        Long userId = securityUtils.getCurrentUserId();
        Menu menu = requireOwnedMenu(request.getMenuId(), userId);
        UberEatsConnection connection = connectionRepository.findByUserIdAndMenuId(userId, menu.getMenuId())
                .orElseGet(() -> UberEatsConnection.builder()
                        .userId(userId)
                        .menuId(menu.getMenuId())
                        .status(UberEatsConnectionStatus.DISCONNECTED)
                        .build());
        connection.setStoreId(request.getStoreId().trim());
        if (hasText(request.getClientId())) {
            connection.setClientIdEncrypted(encryptor.encrypt(request.getClientId().trim()));
        }
        if (hasText(request.getClientSecret())) {
            connection.setClientSecretEncrypted(encryptor.encrypt(request.getClientSecret().trim()));
        }
        if (connection.getClientIdEncrypted() == null || connection.getClientSecretEncrypted() == null) {
            throw new BadRequestException("Uber clientId ve clientSecret zorunludur");
        }
        UberEatsDtos.Credentials credentials = decrypt(connection);
        try {
            uberEatsClient.getMenu(credentials);
            connection.setStatus(UberEatsConnectionStatus.CONNECTED);
            connection.setLastError(null);
            connection.setLastSyncedAt(LocalDateTime.now());
        } catch (UberEatsClientException exception) {
            connection.setStatus(UberEatsConnectionStatus.ERROR);
            connection.setLastError(exception.getMessage());
            throw new BadRequestException("Uber Eats bağlantısı doğrulanamadı: " + exception.getMessage());
        }
        return toResponse(connectionRepository.save(connection));
    }

    @Transactional
    public UberEatsDtos.ConnectionResponse disconnect(Long menuId) {
        UberEatsConnection connection = requireOwnedConnection(menuId);
        connection.setStatus(UberEatsConnectionStatus.DISCONNECTED);
        connection.setLastError(null);
        return toResponse(connectionRepository.save(connection));
    }

    @Transactional(readOnly = true)
    public UberEatsConnection requireConnected(Long menuId) {
        UberEatsConnection connection = connectionRepository.findByMenuId(menuId)
                .orElseThrow(() -> new NotFoundException("Uber Eats bağlantısı bulunamadı"));
        if (connection.getStatus() != UberEatsConnectionStatus.CONNECTED) {
            throw new BadRequestException("Önce Uber Eats mağazasını bağlayın");
        }
        return connection;
    }

    @Transactional(readOnly = true)
    public UberEatsConnection requireOwnedConnection(Long menuId) {
        Long userId = securityUtils.getCurrentUserId();
        requireOwnedMenu(menuId, userId);
        return connectionRepository.findByUserIdAndMenuId(userId, menuId)
                .orElseThrow(() -> new NotFoundException("Uber Eats bağlantısı bulunamadı"));
    }

    public UberEatsDtos.Credentials decrypt(UberEatsConnection connection) {
        return new UberEatsDtos.Credentials(
                encryptor.decrypt(connection.getClientIdEncrypted()),
                encryptor.decrypt(connection.getClientSecretEncrypted()),
                connection.getStoreId()
        );
    }

    public UberEatsDtos.ConnectionResponse toResponse(UberEatsConnection connection) {
        String clientId = null;
        try {
            clientId = encryptor.decrypt(connection.getClientIdEncrypted());
        } catch (IllegalStateException ignored) {
            clientId = null;
        }
        return UberEatsDtos.ConnectionResponse.builder()
                .id(connection.getId())
                .menuId(connection.getMenuId())
                .storeId(connection.getStoreId())
                .clientIdMasked(encryptor.mask(clientId))
                .status(connection.getStatus())
                .lastError(connection.getLastError())
                .lastSyncedAt(connection.getLastSyncedAt())
                .updatedAt(connection.getUpdatedAt())
                .build();
    }

    private Menu requireOwnedMenu(Long menuId, Long userId) {
        Menu menu = menuRepository.findById(menuId)
                .filter(existing -> !existing.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Menü bulunamadı"));
        if (!userId.equals(menu.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu menüye erişim yetkiniz yok");
        }
        return menu;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
