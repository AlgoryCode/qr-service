package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.NotFoundException;
import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.RestaurantTable;
import com.ael.algoryqrservice.model.dto.RestaurantTableDtos;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.repository.RestaurantTableRepository;
import com.ael.algoryqrservice.util.QrCodeGeneratorUtil;
import com.ael.algoryqrservice.util.SecurityUtils;
import com.google.zxing.WriterException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RestaurantTableService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private final RestaurantTableRepository restaurantTableRepository;
    private final MenuRepository menuRepository;
    private final MenuService menuService;
    private final QrCodeGeneratorUtil qrCodeGeneratorUtil;
    private final SecurityUtils securityUtils;

    @Transactional(readOnly = true)
    public List<RestaurantTableDtos.TableResponse> listTables(Long menuId) {
        Menu menu = requireOwnedMenu(menuId);
        return restaurantTableRepository.findByMenuIdOrderByTableNumberAscNameAsc(menu.getMenuId()).stream()
                .map(table -> toTableResponse(table, menu.getQrId()))
                .toList();
    }

    @Transactional
    public RestaurantTableDtos.TableResponse createTable(Long menuId, RestaurantTableDtos.CreateTableRequest request) {
        Menu menu = requireOwnedMenu(menuId);
        if (request == null || request.getName() == null || request.getName().isBlank()) {
            throw new BadRequestException("Masa adı zorunludur");
        }

        String publicToken = generateToken();
        String publicUrl = buildPublicUrl(menu.getQrId(), publicToken);
        String qrImageBase64 = generateQrImage(publicUrl);

        LocalDateTime now = LocalDateTime.now();
        RestaurantTable table = RestaurantTable.builder()
                .menuId(menu.getMenuId())
                .name(request.getName().trim())
                .tableNumber(request.getTableNumber())
                .publicToken(publicToken)
                .qrImageBase64(qrImageBase64)
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build();

        return toTableResponse(restaurantTableRepository.save(table), menu.getQrId());
    }

    @Transactional
    public RestaurantTableDtos.TableResponse updateTable(
            Long menuId,
            Long tableId,
            RestaurantTableDtos.UpdateTableRequest request
    ) {
        Menu menu = requireOwnedMenu(menuId);
        RestaurantTable table = requireTable(menu.getMenuId(), tableId);

        if (request != null) {
            if (request.getName() != null) {
                String name = request.getName().trim();
                if (name.isEmpty()) {
                    throw new BadRequestException("Masa adı boş olamaz");
                }
                table.setName(name);
            }
            if (request.getTableNumber() != null) {
                table.setTableNumber(request.getTableNumber());
            }
            if (request.getActive() != null) {
                table.setActive(request.getActive());
            }
        }

        table.setUpdatedAt(LocalDateTime.now());
        return toTableResponse(restaurantTableRepository.save(table), menu.getQrId());
    }

    @Transactional
    public RestaurantTableDtos.TableResponse regenerateQr(Long menuId, Long tableId) {
        Menu menu = requireOwnedMenu(menuId);
        RestaurantTable table = requireTable(menu.getMenuId(), tableId);

        String publicToken = generateToken();
        String publicUrl = buildPublicUrl(menu.getQrId(), publicToken);
        table.setPublicToken(publicToken);
        table.setQrImageBase64(generateQrImage(publicUrl));
        table.setUpdatedAt(LocalDateTime.now());

        return toTableResponse(restaurantTableRepository.save(table), menu.getQrId());
    }

    @Transactional
    public void deleteTable(Long menuId, Long tableId) {
        Menu menu = requireOwnedMenu(menuId);
        RestaurantTable table = requireTable(menu.getMenuId(), tableId);
        table.setActive(false);
        table.setUpdatedAt(LocalDateTime.now());
        restaurantTableRepository.save(table);
    }

    public RestaurantTableDtos.TableResponse toTableResponse(RestaurantTable table, Long menuQrId) {
        return RestaurantTableDtos.TableResponse.builder()
                .id(table.getId())
                .menuId(table.getMenuId())
                .name(table.getName())
                .tableNumber(table.getTableNumber())
                .publicToken(table.getPublicToken())
                .publicUrl(buildPublicUrl(menuQrId, table.getPublicToken()))
                .qrImageBase64(table.getQrImageBase64())
                .active(table.isActive())
                .createdAt(table.getCreatedAt())
                .updatedAt(table.getUpdatedAt())
                .build();
    }

    private Menu requireOwnedMenu(Long menuId) {
        Menu menu = menuRepository.findById(menuId)
                .filter(m -> !m.isDeleted())
                .orElseThrow(() -> new NotFoundException("Menü bulunamadı"));
        Long currentUserId = securityUtils.getCurrentUserId();
        if (!currentUserId.equals(menu.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu menüye erişim yetkiniz yok");
        }
        return menu;
    }

    private RestaurantTable requireTable(Long menuId, Long tableId) {
        return restaurantTableRepository.findByIdAndMenuId(tableId, menuId)
                .orElseThrow(() -> new NotFoundException("Masa bulunamadı"));
    }

    private String buildPublicUrl(Long menuQrId, String publicToken) {
        return menuService.buildPublicUrlForQrId(menuQrId) + "?t=" + publicToken;
    }

    private String generateQrImage(String publicUrl) {
        try {
            return qrCodeGeneratorUtil.generateBase64Png(publicUrl);
        } catch (WriterException | IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "QR kod oluşturulamadı");
        }
    }

    private static String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
