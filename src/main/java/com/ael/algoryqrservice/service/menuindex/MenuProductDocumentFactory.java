package com.ael.algoryqrservice.service.menuindex;

import com.ael.algoryqrservice.messaging.dto.MenuProductDocumentMessage;
import com.ael.algoryqrservice.model.MenuAllergen;
import com.ael.algoryqrservice.model.MenuCategory;
import com.ael.algoryqrservice.model.MenuProduct;
import com.ael.algoryqrservice.model.MenuSubCategory;
import com.ael.algoryqrservice.model.MenuTag;
import com.ael.algoryqrservice.model.nutrition.NutritionFacts;
import com.ael.algoryqrservice.service.MenuCategoryService;
import com.ael.algoryqrservice.service.MenuTaxonomyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

@Component
@RequiredArgsConstructor
public class MenuProductDocumentFactory {

    private final MenuCategoryService menuCategoryService;
    private final MenuTaxonomyService menuTaxonomyService;

    public record TaxonomySnapshot(
            Map<Long, MenuSubCategory> subCategories,
            Map<Long, MenuCategory> mainCategories,
            Map<Long, MenuTag> tags,
            Map<Long, MenuAllergen> allergens
    ) {
    }

    @Transactional(readOnly = true)
    public TaxonomySnapshot loadTaxonomy(Long menuId) {
        return new TaxonomySnapshot(
                menuCategoryService.loadSubCategoryMap(menuId),
                menuCategoryService.loadCategoryMap(menuId),
                menuTaxonomyService.loadTagMap(),
                menuTaxonomyService.loadAllergenMap()
        );
    }

    public MenuProductDocumentMessage create(MenuProduct product) {
        return create(product, loadTaxonomy(product.getMenuId()));
    }

    public List<MenuProductDocumentMessage> createAll(Collection<MenuProduct> products) {
        if (products == null || products.isEmpty()) {
            return List.of();
        }
        Map<Long, TaxonomySnapshot> byMenu = new HashMap<>();
        return products.stream()
                .map(product -> create(
                        product,
                        byMenu.computeIfAbsent(product.getMenuId(), this::loadTaxonomy)
                ))
                .toList();
    }

    public MenuProductDocumentMessage create(MenuProduct product, TaxonomySnapshot snapshot) {
        MenuSubCategory sub = snapshot.subCategories().get(product.getSubCategoryId());
        MenuCategory main = sub == null ? null : snapshot.mainCategories().get(sub.getMenuCategoryId());
        NutritionFacts nutrition = product.getNutrition();

        return new MenuProductDocumentMessage(
                product.getProductId(),
                product.getMenuId(),
                product.getName(),
                product.getDescription(),
                main == null ? null : main.getSlug(),
                main == null ? null : main.getName(),
                sub == null ? null : sub.getSlug(),
                sub == null ? null : sub.getName(),
                resolve(product.getTagIds(), snapshot.tags(), MenuTag::getSlug),
                resolve(product.getTagIds(), snapshot.tags(), MenuTag::getName),
                resolve(product.getAllergenIds(), snapshot.allergens(), MenuAllergen::getSlug),
                resolve(product.getAllergenIds(), snapshot.allergens(), MenuAllergen::getName),
                product.getPrice(),
                product.getCurrency(),
                product.isAvailable(),
                product.isChefRecommended(),
                product.getServesPeopleMin(),
                product.getServesPeopleMax(),
                nutrition == null ? null : nutrition.getEnergyKcal(),
                nutrition == null ? null : nutrition.getProtein(),
                nutrition == null ? null : nutrition.getFat(),
                nutrition == null ? null : nutrition.getCarbohydrate(),
                nutrition == null ? null : nutrition.getSalt(),
                nullIfZero(product.getRatingAvg()),
                product.getRatingCount(),
                toInstant(product.getUpdatedAt())
        );
    }

    private static <TEntity> List<String> resolve(
            Set<Long> ids,
            Map<Long, TEntity> registry,
            Function<TEntity, String> accessor
    ) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
                .map(registry::get)
                .filter(java.util.Objects::nonNull)
                .map(accessor)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
    }

    private static BigDecimal nullIfZero(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return value;
    }

    private static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
