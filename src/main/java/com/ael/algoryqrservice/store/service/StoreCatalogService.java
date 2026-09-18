package com.ael.algoryqrservice.store.service;

import com.ael.algoryqrservice.model.MenuProduct;
import com.ael.algoryqrservice.model.dto.MenuDtos;
import com.ael.algoryqrservice.model.dto.TaxonomyDtos;
import com.ael.algoryqrservice.repository.MenuProductRepository;
import com.ael.algoryqrservice.repository.MenuSubCategoryRepository;
import com.ael.algoryqrservice.service.MenuCategoryService;
import com.ael.algoryqrservice.service.MenuProductOptionService;
import com.ael.algoryqrservice.store.model.dto.StorePublicDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StoreCatalogService {

    private final MenuProductRepository menuProductRepository;
    private final MenuSubCategoryRepository menuSubCategoryRepository;
    private final MenuCategoryService menuCategoryService;
    private final MenuProductOptionService menuProductOptionService;

    /** Storefront browsing is category-first, so subcategories are flattened into their parent. */
    @Transactional(readOnly = true)
    public List<StorePublicDtos.StoreCategory> listCatalog(Long menuId) {
        List<MenuProduct> products = menuProductRepository
                .findByMenuIdAndDeletedFalseOrderBySortOrderAscProductIdAsc(menuId);
        if (products.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> categoryIdBySubCategoryId = menuSubCategoryRepository
                .findByMenuIdAndDeletedFalseOrderBySortOrderAscIdAsc(menuId)
                .stream()
                .collect(LinkedHashMap::new,
                        (map, sub) -> map.put(sub.getId(), sub.getMenuCategoryId()),
                        Map::putAll);
        Map<Long, List<MenuDtos.MenuProductOptionGroupResponse>> optionsByProduct =
                menuProductOptionService.loadByProductIds(products.stream().map(MenuProduct::getProductId).toList());

        Map<Long, List<StorePublicDtos.StoreProduct>> productsByCategory = new LinkedHashMap<>();
        for (MenuProduct product : products) {
            Long categoryId = categoryIdBySubCategoryId.get(product.getSubCategoryId());
            if (categoryId == null) {
                continue;
            }
            productsByCategory
                    .computeIfAbsent(categoryId, key -> new ArrayList<>())
                    .add(toStoreProduct(product, optionsByProduct.get(product.getProductId())));
        }

        List<StorePublicDtos.StoreCategory> categories = new ArrayList<>();
        for (TaxonomyDtos.MainCategoryResponse category : menuCategoryService.listTaxonomy(menuId)) {
            List<StorePublicDtos.StoreProduct> categoryProducts = productsByCategory.get(category.getId());
            if (categoryProducts == null || categoryProducts.isEmpty()) {
                continue;
            }
            categories.add(StorePublicDtos.StoreCategory.builder()
                    .categoryId(category.getId())
                    .slug(category.getSlug())
                    .name(category.getName())
                    .products(categoryProducts)
                    .build());
        }
        return categories;
    }

    @Transactional(readOnly = true)
    public Map<Long, MenuProduct> loadAvailableProducts(Long menuId, Iterable<Long> productIds) {
        Map<Long, MenuProduct> byId = new LinkedHashMap<>();
        for (Long productId : productIds) {
            menuProductRepository.findByProductIdAndDeletedFalse(productId)
                    .filter(product -> product.getMenuId().equals(menuId) && product.isAvailable())
                    .ifPresent(product -> byId.put(product.getProductId(), product));
        }
        return byId;
    }

    private StorePublicDtos.StoreProduct toStoreProduct(
            MenuProduct product,
            List<MenuDtos.MenuProductOptionGroupResponse> optionGroups
    ) {
        return StorePublicDtos.StoreProduct.builder()
                .productId(product.getProductId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .currency(product.getCurrency())
                .imageUrl(product.getImageUrl())
                .available(product.isAvailable())
                .optionGroups(optionGroups == null ? List.of() : optionGroups)
                .build();
    }
}
