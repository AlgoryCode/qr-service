package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.NotFoundException;
import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.RestaurantTable;
import com.ael.algoryqrservice.model.dto.RestaurantTableDtos;
import com.ael.algoryqrservice.model.RestaurantArea;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.repository.RestaurantAreaRepository;
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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RestaurantTableService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;
    private static final Set<String> LAYOUT_SHAPES = Set.of("ROUND", "SQUARE", "RECTANGLE");

    private final RestaurantTableRepository restaurantTableRepository;
    private final RestaurantAreaRepository restaurantAreaRepository;
    private final MenuRepository menuRepository;
    private final MenuService menuService;
    private final QrCodeGeneratorUtil qrCodeGeneratorUtil;
    private final SecurityUtils securityUtils;

    @Transactional(readOnly = true)
    public List<RestaurantTableDtos.TableResponse> listTables(Long menuId) {
        Menu menu = requireOwnedMenu(menuId);
        Map<Long, String> areaNames = restaurantAreaRepository
                .findByMenuIdOrderBySortOrderAscIdAsc(menu.getMenuId()).stream()
                .collect(Collectors.toMap(RestaurantArea::getId, RestaurantArea::getName));
        return restaurantTableRepository.findByMenuIdOrderByTableNumberAscNameAsc(menu.getMenuId()).stream()
                .filter(table -> !table.isDeleted())
                .map(table -> toTableResponse(table, menu, areaNames.get(table.getAreaId())))
                .toList();
    }

    @Transactional
    public RestaurantTableDtos.TableResponse createTable(Long menuId, RestaurantTableDtos.CreateTableRequest request) {
        Menu menu = requireOwnedMenu(menuId);
        if (request == null || request.getName() == null || request.getName().isBlank()) {
            throw new BadRequestException("Masa adı zorunludur");
        }

        String name = request.getName().trim();
        Long areaId = resolveAreaId(menu.getMenuId(), request.getAreaId());
        if (tableNameTaken(menu.getMenuId(), areaId, name, null)) {
            throw new BadRequestException("Bu alanda aynı masa adı zaten var");
        }

        String publicToken = generateToken();
        String publicUrl = buildPublicUrl(menu, publicToken);
        String qrImageBase64 = generateQrImage(publicUrl);

        LocalDateTime now = LocalDateTime.now();
        RestaurantTable table = RestaurantTable.builder()
                .menuId(menu.getMenuId())
                .areaId(areaId)
                .name(name)
                .tableNumber(request.getTableNumber())
                .capacity(request.getCapacity())
                .layoutX(clampPercent(request.getLayoutX()))
                .layoutY(clampPercent(request.getLayoutY()))
                .layoutRotation(normalizeRotation(request.getLayoutRotation()))
                .layoutShape(normalizeLayoutShape(request.getLayoutShape()))
                .publicToken(publicToken)
                .qrImageBase64(qrImageBase64)
                .active(true)
                .deleted(false)
                .createdAt(now)
                .updatedAt(now)
                .build();

        return toTableResponse(restaurantTableRepository.save(table), menu);
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
                Long areaId = request.getAreaId() != null
                        ? resolveAreaId(menu.getMenuId(), request.getAreaId())
                        : table.getAreaId();
                if (tableNameTaken(menu.getMenuId(), areaId, name, table.getId())) {
                    throw new BadRequestException("Bu alanda aynı masa adı zaten var");
                }
                table.setName(name);
            }
            if (request.getTableNumber() != null) {
                table.setTableNumber(request.getTableNumber());
            }
            if (request.getCapacity() != null) {
                table.setCapacity(request.getCapacity());
            }
            if (request.getActive() != null) {
                table.setActive(request.getActive());
            }
            if (request.getAreaId() != null) {
                Long areaId = resolveAreaId(menu.getMenuId(), request.getAreaId());
                if (tableNameTaken(menu.getMenuId(), areaId, table.getName(), table.getId())) {
                    throw new BadRequestException("Bu alanda aynı masa adı zaten var");
                }
                table.setAreaId(areaId);
            }
            if (request.getLayoutX() != null) {
                table.setLayoutX(clampPercent(request.getLayoutX()));
            }
            if (request.getLayoutY() != null) {
                table.setLayoutY(clampPercent(request.getLayoutY()));
            }
            if (request.getLayoutRotation() != null) {
                table.setLayoutRotation(normalizeRotation(request.getLayoutRotation()));
            }
            if (request.getLayoutShape() != null) {
                table.setLayoutShape(normalizeLayoutShape(request.getLayoutShape()));
            }
        }

        table.setUpdatedAt(LocalDateTime.now());
        return toTableResponse(restaurantTableRepository.save(table), menu);
    }

    @Transactional
    public RestaurantTableDtos.TableResponse regenerateQr(Long menuId, Long tableId) {
        Menu menu = requireOwnedMenu(menuId);
        RestaurantTable table = requireTable(menu.getMenuId(), tableId);

        String publicToken = generateToken();
        String publicUrl = buildPublicUrl(menu, publicToken);
        table.setPublicToken(publicToken);
        table.setQrImageBase64(generateQrImage(publicUrl));
        table.setUpdatedAt(LocalDateTime.now());

        return toTableResponse(restaurantTableRepository.save(table), menu);
    }

    @Transactional
    public void deleteTable(Long menuId, Long tableId) {
        Menu menu = requireOwnedMenu(menuId);
        RestaurantTable table = requireTable(menu.getMenuId(), tableId);
        table.setDeleted(true);
        table.setUpdatedAt(LocalDateTime.now());
        restaurantTableRepository.save(table);
    }

    public RestaurantTableDtos.TableResponse toTableResponse(RestaurantTable table, Menu menu) {
        return toTableResponse(table, menu, resolveAreaName(table.getMenuId(), table.getAreaId()));
    }

    private RestaurantTableDtos.TableResponse toTableResponse(RestaurantTable table, Menu menu, String areaName) {
        return RestaurantTableDtos.TableResponse.builder()
                .id(table.getId())
                .menuId(table.getMenuId())
                .areaId(table.getAreaId())
                .areaName(areaName)
                .name(table.getName())
                .tableNumber(table.getTableNumber())
                .capacity(table.getCapacity())
                .layoutX(table.getLayoutX())
                .layoutY(table.getLayoutY())
                .layoutRotation(table.getLayoutRotation())
                .layoutShape(table.getLayoutShape())
                .publicToken(table.getPublicToken())
                .publicUrl(buildPublicUrl(menu, table.getPublicToken()))
                .qrImageBase64(table.getQrImageBase64())
                .active(table.isActive())
                .createdAt(table.getCreatedAt())
                .updatedAt(table.getUpdatedAt())
                .build();
    }

    private static Double clampPercent(Double value) {
        if (value == null) {
            return null;
        }
        return Math.max(0d, Math.min(100d, value));
    }

    private static Integer normalizeRotation(Integer rotation) {
        if (rotation == null) {
            return null;
        }
        int normalized = rotation % 360;
        if (normalized < 0) {
            normalized += 360;
        }
        return normalized;
    }

    private static String normalizeLayoutShape(String shape) {
        if (shape == null || shape.isBlank()) {
            return null;
        }
        String normalized = shape.trim().toUpperCase(Locale.ROOT);
        if (!LAYOUT_SHAPES.contains(normalized)) {
            throw new BadRequestException("Geçersiz masa şekli");
        }
        return normalized;
    }

    private Long resolveAreaId(Long menuId, Long areaId) {
        if (areaId == null) {
            return null;
        }
        restaurantAreaRepository.findByIdAndMenuId(areaId, menuId)
                .orElseThrow(() -> new BadRequestException("Alan bulunamadı"));
        return areaId;
    }

    private boolean tableNameTaken(Long menuId, Long areaId, String name, Long excludeId) {
        return restaurantTableRepository.findByMenuIdOrderByTableNumberAscNameAsc(menuId).stream()
                .filter(table -> !table.isDeleted())
                .filter(table -> Objects.equals(table.getAreaId(), areaId))
                .filter(table -> excludeId == null || !excludeId.equals(table.getId()))
                .anyMatch(table -> namesEqual(table.getName(), name));
    }

    private static boolean namesEqual(String left, String right) {
        return normalizeName(left).equals(normalizeName(right));
    }

    private static String normalizeName(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toLowerCase(Locale.forLanguageTag("tr"));
    }

    private String resolveAreaName(Long menuId, Long areaId) {
        if (areaId == null) {
            return null;
        }
        return restaurantAreaRepository.findByIdAndMenuId(areaId, menuId)
                .map(RestaurantArea::getName)
                .orElse(null);
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
                .filter(table -> !table.isDeleted())
                .orElseThrow(() -> new NotFoundException("Masa bulunamadı"));
    }

    private String buildPublicUrl(Menu menu, String publicToken) {
        return menuService.buildPublicUrl(menu) + "/content?t=" + publicToken;
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
