package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.MenuProduct;
import com.ael.algoryqrservice.model.MenuProductPairing;
import com.ael.algoryqrservice.model.dto.MenuDtos;
import com.ael.algoryqrservice.repository.MenuProductPairingRepository;
import com.ael.algoryqrservice.repository.MenuProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MenuProductPairingService {

    private final MenuProductPairingRepository menuProductPairingRepository;
    private final MenuProductRepository menuProductRepository;
    private final MenuTaxonomyService menuTaxonomyService;

    @Transactional(readOnly = true)
    public MenuDtos.MenuProductPairingsResponse load(Long productId) {
        return toResponse(menuProductPairingRepository.findByProductIdOrderBySortOrderAscIdAsc(productId));
    }

    @Transactional(readOnly = true)
    public Map<Long, MenuDtos.MenuProductPairingsResponse> loadByProductIds(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        List<MenuProductPairing> rows = menuProductPairingRepository.findByProductIdInOrderBySortOrderAscIdAsc(productIds);
        Map<Long, List<MenuProductPairing>> grouped = new java.util.LinkedHashMap<>();
        for (MenuProductPairing row : rows) {
            grouped.computeIfAbsent(row.getProductId(), ignored -> new ArrayList<>()).add(row);
        }
        Map<Long, MenuDtos.MenuProductPairingsResponse> result = new java.util.LinkedHashMap<>();
        for (Long productId : productIds) {
            result.put(productId, toResponse(grouped.getOrDefault(productId, List.of())));
        }
        return result;
    }

    @Transactional
    public void replace(Long productId, Long menuId, MenuDtos.MenuProductPairingsRequest request) {
        menuProductPairingRepository.deleteByProductId(productId);
        if (request == null) {
            return;
        }
        List<MenuProductPairing> rows = buildRows(productId, menuId, request);
        if (!rows.isEmpty()) {
            menuProductPairingRepository.saveAll(rows);
        }
    }

    @Transactional
    public void copyPairings(Map<Long, Long> sourceToTargetProductId) {
        if (sourceToTargetProductId == null || sourceToTargetProductId.isEmpty()) {
            return;
        }
        List<MenuProductPairing> sourceRows = menuProductPairingRepository
                .findByProductIdInOrderBySortOrderAscIdAsc(sourceToTargetProductId.keySet());
        if (sourceRows.isEmpty()) {
            return;
        }
        List<MenuProductPairing> copies = new ArrayList<>();
        for (MenuProductPairing source : sourceRows) {
            Long newProductId = sourceToTargetProductId.get(source.getProductId());
            if (newProductId == null) {
                continue;
            }
            Long targetProductId = source.getTargetProductId();
            if (targetProductId != null) {
                Long remapped = sourceToTargetProductId.get(targetProductId);
                if (remapped == null) {
                    continue;
                }
                copies.add(MenuProductPairing.builder()
                        .productId(newProductId)
                        .targetProductId(remapped)
                        .sortOrder(source.getSortOrder())
                        .build());
                continue;
            }
            copies.add(MenuProductPairing.builder()
                    .productId(newProductId)
                    .targetSubCategoryId(source.getTargetSubCategoryId())
                    .targetMainCategoryId(source.getTargetMainCategoryId())
                    .sortOrder(source.getSortOrder())
                    .build());
        }
        if (!copies.isEmpty()) {
            menuProductPairingRepository.saveAll(copies);
        }
    }

    private List<MenuProductPairing> buildRows(
            Long productId,
            Long menuId,
            MenuDtos.MenuProductPairingsRequest request
    ) {
        List<MenuProductPairing> rows = new ArrayList<>();
        int sortOrder = 0;
        Set<Long> productIds = uniqueIds(request.getProductIds());
        productIds.remove(productId);
        if (!productIds.isEmpty()) {
            List<Long> productIdList = List.copyOf(productIds);
            List<MenuProduct> targets = menuProductRepository.findByProductIdInAndDeletedFalse(productIdList);
            if (targets.size() != productIds.size()) {
                throw new BadRequestException("Eşlik ürünü bulunamadı");
            }
            for (MenuProduct target : targets) {
                if (!Objects.equals(target.getMenuId(), menuId)) {
                    throw new BadRequestException("Eşlik ürünü aynı menüde olmalıdır");
                }
            }
            for (Long targetId : productIds) {
                rows.add(MenuProductPairing.builder()
                        .productId(productId)
                        .targetProductId(targetId)
                        .sortOrder(sortOrder++)
                        .build());
            }
        }
        for (Long mainId : uniqueIds(request.getMainCategoryIds())) {
            menuTaxonomyService.requireMainCategory(mainId);
            rows.add(MenuProductPairing.builder()
                    .productId(productId)
                    .targetMainCategoryId(mainId)
                    .sortOrder(sortOrder++)
                    .build());
        }
        for (Long subId : uniqueIds(request.getSubCategoryIds())) {
            menuTaxonomyService.requireSubCategory(subId);
            rows.add(MenuProductPairing.builder()
                    .productId(productId)
                    .targetSubCategoryId(subId)
                    .sortOrder(sortOrder++)
                    .build());
        }
        return rows;
    }

    private static Set<Long> uniqueIds(List<Long> values) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (values == null) {
            return ids;
        }
        for (Long value : values) {
            if (value != null && value > 0) {
                ids.add(value);
            }
        }
        return ids;
    }

    private static MenuDtos.MenuProductPairingsResponse toResponse(List<MenuProductPairing> rows) {
        List<Long> productIds = new ArrayList<>();
        List<Long> mainCategoryIds = new ArrayList<>();
        List<Long> subCategoryIds = new ArrayList<>();
        for (MenuProductPairing row : rows) {
            if (row.getTargetProductId() != null) {
                productIds.add(row.getTargetProductId());
            } else if (row.getTargetMainCategoryId() != null) {
                mainCategoryIds.add(row.getTargetMainCategoryId());
            } else if (row.getTargetSubCategoryId() != null) {
                subCategoryIds.add(row.getTargetSubCategoryId());
            }
        }
        return MenuDtos.MenuProductPairingsResponse.builder()
                .productIds(productIds)
                .mainCategoryIds(mainCategoryIds)
                .subCategoryIds(subCategoryIds)
                .build();
    }
}
