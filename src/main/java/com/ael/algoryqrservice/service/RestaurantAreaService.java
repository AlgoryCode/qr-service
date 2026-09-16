package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.NotFoundException;
import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.RestaurantArea;
import com.ael.algoryqrservice.model.RestaurantTable;
import com.ael.algoryqrservice.model.dto.RestaurantAreaDtos;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.repository.RestaurantAreaRepository;
import com.ael.algoryqrservice.repository.RestaurantTableRepository;
import com.ael.algoryqrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class RestaurantAreaService {

    private final RestaurantAreaRepository restaurantAreaRepository;
    private final RestaurantTableRepository restaurantTableRepository;
    private final MenuRepository menuRepository;
    private final SecurityUtils securityUtils;

    @Transactional(readOnly = true)
    public List<RestaurantAreaDtos.AreaResponse> listAreas(Long menuId) {
        Menu menu = requireOwnedMenu(menuId);
        return restaurantAreaRepository.findByMenuIdOrderBySortOrderAscIdAsc(menu.getMenuId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public RestaurantAreaDtos.AreaResponse createArea(Long menuId, RestaurantAreaDtos.CreateAreaRequest request) {
        Menu menu = requireOwnedMenu(menuId);
        String name = requireName(request == null ? null : request.getName());
        if (areaNameTaken(menu.getMenuId(), name, null)) {
            throw new BadRequestException("Bu alan adı zaten kullanılıyor");
        }

        int sortOrder = restaurantAreaRepository.findByMenuIdOrderBySortOrderAscIdAsc(menu.getMenuId()).stream()
                .mapToInt(RestaurantArea::getSortOrder)
                .max()
                .orElse(0) + 10;

        LocalDateTime now = LocalDateTime.now();
        RestaurantArea area = RestaurantArea.builder()
                .menuId(menu.getMenuId())
                .name(name)
                .sortOrder(sortOrder)
                .createdAt(now)
                .updatedAt(now)
                .build();
        return toResponse(restaurantAreaRepository.save(area));
    }

    @Transactional
    public RestaurantAreaDtos.AreaResponse updateArea(
            Long menuId,
            Long areaId,
            RestaurantAreaDtos.UpdateAreaRequest request
    ) {
        Menu menu = requireOwnedMenu(menuId);
        RestaurantArea area = requireArea(menu.getMenuId(), areaId);
        if (request != null && request.getName() != null) {
            String name = requireName(request.getName());
            if (areaNameTaken(menu.getMenuId(), name, area.getId())) {
                throw new BadRequestException("Bu alan adı zaten kullanılıyor");
            }
            area.setName(name);
        }
        area.setUpdatedAt(LocalDateTime.now());
        return toResponse(restaurantAreaRepository.save(area));
    }

    @Transactional
    public void deleteArea(Long menuId, Long areaId) {
        Menu menu = requireOwnedMenu(menuId);
        RestaurantArea area = requireArea(menu.getMenuId(), areaId);
        List<RestaurantTable> tables = restaurantTableRepository.findByMenuIdAndAreaId(menu.getMenuId(), area.getId());
        for (RestaurantTable table : tables) {
            table.setAreaId(null);
            table.setUpdatedAt(LocalDateTime.now());
        }
        restaurantTableRepository.saveAll(tables);
        restaurantAreaRepository.delete(area);
    }

    public RestaurantArea requireArea(Long menuId, Long areaId) {
        return restaurantAreaRepository.findByIdAndMenuId(areaId, menuId)
                .orElseThrow(() -> new NotFoundException("Alan bulunamadı"));
    }

    private String requireName(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BadRequestException("Alan adı zorunludur");
        }
        return raw.trim();
    }

    private boolean areaNameTaken(Long menuId, String name, Long excludeId) {
        return restaurantAreaRepository.findByMenuIdOrderBySortOrderAscIdAsc(menuId).stream()
                .filter(area -> excludeId == null || !excludeId.equals(area.getId()))
                .anyMatch(area -> normalizeName(area.getName()).equals(normalizeName(name)));
    }

    private static String normalizeName(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toLowerCase(Locale.forLanguageTag("tr"));
    }

    private RestaurantAreaDtos.AreaResponse toResponse(RestaurantArea area) {
        return RestaurantAreaDtos.AreaResponse.builder()
                .id(area.getId())
                .menuId(area.getMenuId())
                .name(area.getName())
                .sortOrder(area.getSortOrder())
                .createdAt(area.getCreatedAt())
                .updatedAt(area.getUpdatedAt())
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
}
