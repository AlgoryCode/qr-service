package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.NotFoundException;
import com.ael.algoryqrservice.model.Branch;
import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.RestaurantArea;
import com.ael.algoryqrservice.model.RestaurantLayout;
import com.ael.algoryqrservice.model.RestaurantTable;
import com.ael.algoryqrservice.model.dto.RestaurantLayoutDtos;
import com.ael.algoryqrservice.model.enums.MenuChannel;
import com.ael.algoryqrservice.repository.BranchRepository;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.repository.RestaurantAreaRepository;
import com.ael.algoryqrservice.repository.RestaurantLayoutRepository;
import com.ael.algoryqrservice.repository.RestaurantTableRepository;
import com.ael.algoryqrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RestaurantLayoutService {

    private final RestaurantLayoutRepository restaurantLayoutRepository;
    private final RestaurantAreaRepository restaurantAreaRepository;
    private final RestaurantTableRepository restaurantTableRepository;
    private final BranchRepository branchRepository;
    private final MenuRepository menuRepository;
    private final RestaurantTableService restaurantTableService;
    private final SecurityUtils securityUtils;

    @Transactional(readOnly = true)
    public List<RestaurantLayoutDtos.LayoutResponse> listLayouts() {
        Long userId = securityUtils.getCurrentMerchantId();
        List<RestaurantLayout> layouts = restaurantLayoutRepository.findByUserIdOrderByIdAsc(userId);
        Map<Long, String> branchNames = branchNames(userId);
        Set<Long> menuBranches = menuBranches(branchNames.keySet());
        Map<Long, Integer> areaCounts = new HashMap<>();
        for (RestaurantArea area : restaurantAreaRepository.findByUserIdOrderBySortOrderAscIdAsc(userId)) {
            if (area.getLayoutId() == null) {
                continue;
            }
            areaCounts.merge(area.getLayoutId(), 1, Integer::sum);
        }
        Map<Long, Integer> tableCounts = new HashMap<>();
        for (RestaurantTable table : restaurantTableRepository.findByUserIdAndDeletedFalseOrderByTableNumberAscNameAsc(userId)) {
            if (table.getLayoutId() == null) {
                continue;
            }
            tableCounts.merge(table.getLayoutId(), 1, Integer::sum);
        }
        return layouts.stream()
                .map(layout -> toResponse(
                        layout,
                        branchNames,
                        menuBranches,
                        areaCounts.getOrDefault(layout.getId(), 0),
                        tableCounts.getOrDefault(layout.getId(), 0)
                ))
                .toList();
    }

    @Transactional
    public RestaurantLayoutDtos.LayoutResponse createLayout(RestaurantLayoutDtos.CreateLayoutRequest request) {
        Long userId = securityUtils.getCurrentMerchantId();
        String name = requireName(request == null ? null : request.getName());
        requireUniqueName(userId, name, null);
        LocalDateTime now = LocalDateTime.now();
        RestaurantLayout layout = restaurantLayoutRepository.save(RestaurantLayout.builder()
                .userId(userId)
                .name(name)
                .branchId(null)
                .createdAt(now)
                .updatedAt(now)
                .build());
        return toResponse(layout, branchNames(userId), menuBranches(Set.of()), 0, 0);
    }

    @Transactional
    public RestaurantLayoutDtos.LayoutResponse renameLayout(
            Long layoutId,
            RestaurantLayoutDtos.UpdateLayoutRequest request
    ) {
        Long userId = securityUtils.getCurrentMerchantId();
        RestaurantLayout layout = requireOwnedLayout(userId, layoutId);
        if (request != null && request.getName() != null) {
            String name = requireName(request.getName());
            requireUniqueName(userId, name, layout.getId());
            layout.setName(name);
            layout.setUpdatedAt(LocalDateTime.now());
            restaurantLayoutRepository.save(layout);
        }
        return toCountedResponse(userId, layout);
    }

    @Transactional
    public void deleteLayout(Long layoutId) {
        Long userId = securityUtils.getCurrentMerchantId();
        RestaurantLayout layout = requireOwnedLayout(userId, layoutId);
        LocalDateTime now = LocalDateTime.now();
        List<RestaurantTable> tables = restaurantTableRepository
                .findByLayoutIdAndDeletedFalseOrderByTableNumberAscNameAsc(layout.getId());
        for (RestaurantTable table : tables) {
            table.setDeleted(true);
            table.setUpdatedAt(now);
        }
        restaurantTableRepository.saveAll(tables);
        restaurantAreaRepository.deleteAll(
                restaurantAreaRepository.findByLayoutIdOrderBySortOrderAscIdAsc(layout.getId())
        );
        restaurantLayoutRepository.delete(layout);
    }

    @Transactional
    public RestaurantLayoutDtos.LayoutResponse assignBranch(Long layoutId, Long branchId) {
        Long userId = securityUtils.getCurrentMerchantId();
        RestaurantLayout layout = requireOwnedLayout(userId, layoutId);
        if (branchId == null) {
            layout.setBranchId(null);
            layout.setUpdatedAt(LocalDateTime.now());
            restaurantLayoutRepository.save(layout);
            restaurantTableService.rebindLayoutMenu(layout.getId(), null);
            return toCountedResponse(userId, layout);
        }

        Branch branch = branchRepository.findByIdAndUserIdAndDeletedFalse(branchId, userId)
                .orElseThrow(() -> new BadRequestException("Şube bulunamadı"));
        LocalDateTime now = LocalDateTime.now();
        for (RestaurantLayout other : restaurantLayoutRepository.findByUserIdAndBranchId(userId, branch.getId())) {
            if (other.getId().equals(layout.getId())) {
                continue;
            }
            other.setBranchId(null);
            other.setUpdatedAt(now);
            restaurantLayoutRepository.save(other);
            restaurantTableService.rebindLayoutMenu(other.getId(), null);
        }
        restaurantLayoutRepository.flush();

        layout.setBranchId(branch.getId());
        layout.setUpdatedAt(now);
        restaurantLayoutRepository.save(layout);
        Menu menu = menuRepository.findByBranchIdAndChannelAndDeletedFalse(branch.getId(), MenuChannel.QR)
                .stream()
                .findFirst()
                .orElse(null);
        restaurantTableService.rebindLayoutMenu(layout.getId(), menu);
        return toCountedResponse(userId, layout);
    }

    public RestaurantLayout requireOwnedLayout(Long userId, Long layoutId) {
        if (layoutId == null) {
            throw new NotFoundException("Düzen bulunamadı");
        }
        return restaurantLayoutRepository.findByIdAndUserId(layoutId, userId)
                .orElseThrow(() -> new NotFoundException("Düzen bulunamadı"));
    }

    private RestaurantLayoutDtos.LayoutResponse toCountedResponse(Long userId, RestaurantLayout layout) {
        int areaCount = restaurantAreaRepository.findByLayoutIdOrderBySortOrderAscIdAsc(layout.getId()).size();
        int tableCount = restaurantTableRepository
                .findByLayoutIdAndDeletedFalseOrderByTableNumberAscNameAsc(layout.getId())
                .size();
        Map<Long, String> branchNames = branchNames(userId);
        return toResponse(layout, branchNames, menuBranches(branchNames.keySet()), areaCount, tableCount);
    }

    private Map<Long, String> branchNames(Long userId) {
        Map<Long, String> names = new HashMap<>();
        for (Branch branch : branchRepository.findByUserIdAndDeletedFalse(userId)) {
            names.put(branch.getId(), branch.getName());
        }
        return names;
    }

    private Set<Long> menuBranches(Set<Long> branchIds) {
        Set<Long> linked = new HashSet<>();
        for (Long branchId : branchIds) {
            if (!menuRepository.findByBranchIdAndChannelAndDeletedFalse(branchId, MenuChannel.QR).isEmpty()) {
                linked.add(branchId);
            }
        }
        return linked;
    }

    private RestaurantLayoutDtos.LayoutResponse toResponse(
            RestaurantLayout layout,
            Map<Long, String> branchNames,
            Set<Long> menuBranches,
            int areaCount,
            int tableCount
    ) {
        Long branchId = layout.getBranchId();
        return RestaurantLayoutDtos.LayoutResponse.builder()
                .id(layout.getId())
                .name(layout.getName())
                .branchId(branchId)
                .branchName(branchId == null ? null : branchNames.get(branchId))
                .areaCount(areaCount)
                .tableCount(tableCount)
                .menuLinked(branchId != null && menuBranches.contains(branchId))
                .build();
    }

    private void requireUniqueName(Long userId, String name, Long excludeId) {
        boolean taken = restaurantLayoutRepository.findByUserIdOrderByIdAsc(userId).stream()
                .filter(layout -> excludeId == null || !excludeId.equals(layout.getId()))
                .anyMatch(layout -> normalizeName(layout.getName()).equals(normalizeName(name)));
        if (taken) {
            throw new BadRequestException("Bu düzen adı zaten kullanılıyor");
        }
    }

    private static String requireName(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BadRequestException("Düzen adı zorunludur");
        }
        String name = raw.trim();
        if (name.length() > 120) {
            throw new BadRequestException("Düzen adı en fazla 120 karakter olabilir");
        }
        return name;
    }

    private static String normalizeName(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toLowerCase(Locale.forLanguageTag("tr"));
    }
}
