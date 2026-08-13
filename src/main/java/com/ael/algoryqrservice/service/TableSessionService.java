package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.NotFoundException;
import com.ael.algoryqrservice.exception.UnauthorizedException;
import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.RestaurantTable;
import com.ael.algoryqrservice.model.TableSession;
import com.ael.algoryqrservice.model.dto.RestaurantTableDtos;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.repository.RestaurantTableRepository;
import com.ael.algoryqrservice.repository.TableSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TableSessionService {

    public static final String TABLE_SESSION_HEADER = "X-Table-Session";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;
    private static final int SESSION_TTL_HOURS = 4;

    private final TableSessionRepository tableSessionRepository;
    private final RestaurantTableRepository restaurantTableRepository;
    private final MenuRepository menuRepository;

    private static final String WALK_IN_TABLE_NAME = "Misafir";

    @Transactional
    public RestaurantTableDtos.TableSessionResponse openSession(Long qrId, String tableToken) {
        Menu menu = menuRepository.findByQrIdAndActiveTrueAndDeletedFalse(qrId)
                .orElseThrow(() -> new NotFoundException("Menü bulunamadı"));
        if (!menu.isPublicAccessEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Lütfen restoran sahibiyle iletişime geçiniz.");
        }

        RestaurantTable table;
        if (tableToken != null && !tableToken.isBlank()) {
            table = restaurantTableRepository.findByPublicTokenAndActiveTrue(tableToken.trim())
                    .orElseThrow(() -> new NotFoundException("Masa bulunamadı"));
            if (!table.getMenuId().equals(menu.getMenuId())) {
                throw new BadRequestException("Masa bu menüye ait değil");
            }
        } else {
            table = resolveWalkInTable(menu);
        }

        LocalDateTime now = LocalDateTime.now();
        TableSession session = TableSession.builder()
                .id(UUID.randomUUID())
                .tableId(table.getId())
                .menuId(menu.getMenuId())
                .sessionToken(generateToken())
                .expiresAt(now.plusHours(SESSION_TTL_HOURS))
                .revoked(false)
                .createdAt(now)
                .build();

        tableSessionRepository.save(session);

        return RestaurantTableDtos.TableSessionResponse.builder()
                .sessionToken(session.getSessionToken())
                .tableId(table.getId())
                .menuId(menu.getMenuId())
                .tableName(table.getName())
                .expiresAt(session.getExpiresAt())
                .build();
    }

    private RestaurantTable resolveWalkInTable(Menu menu) {
        return restaurantTableRepository
                .findFirstByMenuIdAndNameIgnoreCaseAndActiveTrue(menu.getMenuId(), WALK_IN_TABLE_NAME)
                .or(() -> restaurantTableRepository.findFirstByMenuIdAndActiveTrueOrderByTableNumberAscNameAsc(menu.getMenuId()))
                .orElseGet(() -> {
                    LocalDateTime now = LocalDateTime.now();
                    String publicToken = generateToken();
                    RestaurantTable created = RestaurantTable.builder()
                            .menuId(menu.getMenuId())
                            .name(WALK_IN_TABLE_NAME)
                            .tableNumber(null)
                            .publicToken(publicToken)
                            .qrImageBase64(null)
                            .active(true)
                            .createdAt(now)
                            .updatedAt(now)
                            .build();
                    return restaurantTableRepository.save(created);
                });
    }

    @Transactional
    public TableSession openInternalSession(Long menuId, Long tableId) {
        if (menuId == null || tableId == null) {
            throw new BadRequestException("Menü ve masa zorunludur");
        }
        LocalDateTime now = LocalDateTime.now();
        TableSession session = TableSession.builder()
                .id(UUID.randomUUID())
                .tableId(tableId)
                .menuId(menuId)
                .sessionToken(generateToken())
                .expiresAt(now.plusHours(SESSION_TTL_HOURS))
                .revoked(false)
                .createdAt(now)
                .build();
        return tableSessionRepository.save(session);
    }

    @Transactional(readOnly = true)
    public TableSession requireActiveSession(String sessionToken) {
        if (sessionToken == null || sessionToken.isBlank()) {
            throw new UnauthorizedException("Masa oturumu gerekli");
        }

        TableSession session = tableSessionRepository.findBySessionTokenAndRevokedFalse(sessionToken.trim())
                .orElseThrow(() -> new UnauthorizedException("Masa oturumu geçersiz"));

        if (session.isExpired()) {
            throw new UnauthorizedException("Masa oturumu süresi dolmuş");
        }

        return session;
    }

    private static String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
