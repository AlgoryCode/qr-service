package com.ael.algoryqrservice.qrservice.integration.service.ubereatsmenu;

import com.ael.algoryqrservice.qrservice.exception.BadRequestException;
import com.ael.algoryqrservice.qrservice.exception.NotFoundException;
import com.ael.algoryqrservice.qrservice.integration.ubereatsmenu.client.UberEatsMenuAuthClient;
import com.ael.algoryqrservice.qrservice.integration.ubereatsmenu.client.UberEatsMenuClientException;
import com.ael.algoryqrservice.qrservice.integration.ubereatsmenu.crypto.UberEatsMenuCredentialEncryptor;
import com.ael.algoryqrservice.qrservice.integration.ubereatsmenu.model.UberEatsMenuConnection;
import com.ael.algoryqrservice.qrservice.integration.ubereatsmenu.model.UberEatsMenuConnectionStatus;
import com.ael.algoryqrservice.qrservice.integration.ubereatsmenu.model.dto.UberEatsMenuDtos;
import com.ael.algoryqrservice.qrservice.integration.ubereatsmenu.repository.UberEatsMenuConnectionRepository;
import com.ael.algoryqrservice.qrservice.model.Menu;
import com.ael.algoryqrservice.qrservice.repository.MenuRepository;
import com.ael.algoryqrservice.qrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UberEatsMenuConnectionService {

    private final UberEatsMenuConnectionRepository connectionRepository;
    private final MenuRepository menuRepository;
    private final UberEatsMenuCredentialEncryptor encryptor;
    private final UberEatsMenuAuthClient uberEatsMenuAuthClient;
    private final SecurityUtils securityUtils;

    @Transactional(readOnly = true)
    public List<UberEatsMenuDtos.ConnectionResponse> listMine() {
        return connectionRepository.findByMerchantIdOrderByUpdatedAtDesc(securityUtils.getCurrentMerchantId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public UberEatsMenuDtos.ConnectionResponse getMine(Long menuId) {
        return toResponse(requireOwnedConnection(menuId));
    }

    @Transactional
    public UberEatsMenuDtos.ConnectionResponse upsert(UberEatsMenuDtos.UpsertConnectionRequest request) {
        Long merchantId = securityUtils.getCurrentMerchantId();
        Menu menu = requireOwnedMenu(request.getMenuId(), merchantId);
        UberEatsMenuConnection connection = connectionRepository.findByMerchantIdAndMenuId(merchantId, menu.getMenuId())
                .orElseGet(() -> UberEatsMenuConnection.builder()
                        .merchantId(merchantId)
                        .menuId(menu.getMenuId())
                        .status(UberEatsMenuConnectionStatus.DISCONNECTED)
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
        UberEatsMenuDtos.Credentials credentials = decrypt(connection);
        try {
            uberEatsMenuAuthClient.getMenu(credentials);
            connection.setStatus(UberEatsMenuConnectionStatus.CONNECTED);
            connection.setLastError(null);
            connection.setLastSyncedAt(LocalDateTime.now());
        } catch (UberEatsMenuClientException exception) {
            connection.setStatus(UberEatsMenuConnectionStatus.ERROR);
            connection.setLastError(exception.getMessage());
            throw new BadRequestException("Uber Eats bağlantısı doğrulanamadı: " + exception.getMessage());
        }
        return toResponse(connectionRepository.save(connection));
    }

    @Transactional
    public UberEatsMenuDtos.ConnectionResponse disconnect(Long menuId) {
        UberEatsMenuConnection connection = requireOwnedConnection(menuId);
        connection.setStatus(UberEatsMenuConnectionStatus.DISCONNECTED);
        connection.setLastError(null);
        return toResponse(connectionRepository.save(connection));
    }

    @Transactional(readOnly = true)
    public UberEatsMenuConnection requireConnected(Long menuId) {
        UberEatsMenuConnection connection = connectionRepository.findByMenuId(menuId)
                .orElseThrow(() -> new NotFoundException("Uber Eats bağlantısı bulunamadı"));
        if (connection.getStatus() != UberEatsMenuConnectionStatus.CONNECTED) {
            throw new BadRequestException("Önce Uber Eats mağazasını bağlayın");
        }
        return connection;
    }

    @Transactional(readOnly = true)
    public UberEatsMenuConnection requireOwnedConnection(Long menuId) {
        Long merchantId = securityUtils.getCurrentMerchantId();
        requireOwnedMenu(menuId, merchantId);
        return connectionRepository.findByMerchantIdAndMenuId(merchantId, menuId)
                .orElseThrow(() -> new NotFoundException("Uber Eats bağlantısı bulunamadı"));
    }

    public UberEatsMenuDtos.Credentials decrypt(UberEatsMenuConnection connection) {
        return new UberEatsMenuDtos.Credentials(
                encryptor.decrypt(connection.getClientIdEncrypted()),
                encryptor.decrypt(connection.getClientSecretEncrypted()),
                connection.getStoreId()
        );
    }

    public UberEatsMenuDtos.ConnectionResponse toResponse(UberEatsMenuConnection connection) {
        String clientId = null;
        try {
            clientId = encryptor.decrypt(connection.getClientIdEncrypted());
        } catch (IllegalStateException ignored) {
            clientId = null;
        }
        return UberEatsMenuDtos.ConnectionResponse.builder()
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

    private Menu requireOwnedMenu(Long menuId, Long merchantId) {
        Menu menu = menuRepository.findById(menuId)
                .filter(existing -> !existing.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Menü bulunamadı"));
        if (!merchantId.equals(menu.getMerchantId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu menüye erişim yetkiniz yok");
        }
        return menu;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
