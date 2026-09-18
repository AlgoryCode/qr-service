package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogProducts;
import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.MenuProduct;
import com.ael.algoryqrservice.repository.MenuProductRepository;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.service.entitlement.FeatureUsageSyncRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MenuCatalogCloneService {

    private final MenuRepository menuRepository;
    private final MenuProductRepository menuProductRepository;
    private final MenuCategoryService menuCategoryService;
    private final MenuProductPairingService menuProductPairingService;
    private final MenuProductOptionService menuProductOptionService;
    private final ProductImageStorageService productImageStorageService;
    private final EntitlementService entitlementService;
    private final FeatureUsageSyncRegistry usageSyncRegistry;

    @Transactional
    public int cloneInto(Menu targetMenu, Long sourceMenuId, Long userId) {
        Menu sourceMenu = requireCloneableSource(sourceMenuId, userId);

        List<MenuProduct> sourceProducts = menuProductRepository
                .findByMenuIdAndDeletedFalseOrderBySortOrderAscProductIdAsc(sourceMenu.getMenuId());
        if (sourceProducts.isEmpty()) {
            return 0;
        }
        entitlementService.assertMenuProductCreationAllowed(userId, sourceProducts.size());

        MenuCategoryService.TaxonomyCloneResult taxonomy = menuCategoryService.cloneTaxonomyToMenu(
                sourceMenu.getMenuId(),
                targetMenu.getMenuId()
        );
        Map<Long, Long> subCategoryIds = taxonomy.subCategoryIds();
        Map<Long, Long> productIds = new HashMap<>();

        for (MenuProduct source : sourceProducts) {
            Long targetSubCategoryId = subCategoryIds.get(source.getSubCategoryId());
            if (targetSubCategoryId == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Kaynak ürün kategorisi hedef menüye kopyalanamadı: " + source.getName()
                );
            }
            MenuProduct saved = menuProductRepository.save(copyProduct(source, targetMenu.getMenuId(), targetSubCategoryId));
            productIds.put(source.getProductId(), saved.getProductId());
        }

        menuProductPairingService.copyPairings(productIds, taxonomy.categoryIds(), subCategoryIds);
        menuProductOptionService.copyOptions(productIds);
        usageSyncRegistry.synchronize(userId, CatalogProducts.MENU_PRODUCT);
        return productIds.size();
    }

    @Transactional(readOnly = true)
    public int countCloneableProducts(Long sourceMenuId, Long userId) {
        requireCloneableSource(sourceMenuId, userId);
        return menuProductRepository
                .findByMenuIdAndDeletedFalseOrderBySortOrderAscProductIdAsc(sourceMenuId)
                .size();
    }

    private Menu requireCloneableSource(Long sourceMenuId, Long userId) {
        Menu sourceMenu = menuRepository.findById(sourceMenuId)
                .filter(menu -> !menu.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kaynak menü bulunamadı"));
        if (!userId.equals(sourceMenu.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu menüye erişim yetkiniz yok");
        }
        if (!sourceMenu.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Yalnızca aktif menülerden kopyalama yapılabilir");
        }
        return sourceMenu;
    }

    private MenuProduct copyProduct(MenuProduct source, Long targetMenuId, Long targetSubCategoryId) {
        Set<Long> tagIds = source.getTagIds() == null ? new HashSet<>() : new HashSet<>(source.getTagIds());
        Set<Long> allergenIds = source.getAllergenIds() == null ? new HashSet<>() : new HashSet<>(source.getAllergenIds());
        return MenuProduct.builder()
                .menuId(targetMenuId)
                .name(source.getName())
                .description(source.getDescription())
                .price(source.getPrice())
                .currency(source.getCurrency())
                .subCategoryId(targetSubCategoryId)
                .tagIds(tagIds)
                .allergenIds(allergenIds)
                .chefRecommended(source.isChefRecommended())
                .sortOrder(source.getSortOrder())
                .imageUrl(resolveProductImageUrl(source.getImageUrl()))
                .available(source.isAvailable())
                .servesPeopleMin(source.getServesPeopleMin())
                .servesPeopleMax(source.getServesPeopleMax())
                .nutrition(source.getNutrition())
                .ratingAvg(BigDecimal.ZERO)
                .ratingCount(0L)
                .build();
    }

    private String resolveProductImageUrl(String imageUrl) {
        String normalized = imageUrl == null || imageUrl.isBlank() ? null : imageUrl.trim();
        productImageStorageService.validateImageUrl(normalized);
        return normalized;
    }
}
