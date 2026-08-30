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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MenuProductDocumentFactoryTest {

    @Mock
    private MenuCategoryService menuCategoryService;
    @Mock
    private MenuTaxonomyService menuTaxonomyService;

    @InjectMocks
    private MenuProductDocumentFactory factory;

    @BeforeEach
    void stubTaxonomy() {
        MenuSubCategory sub = new MenuSubCategory();
        sub.setId(20L);
        sub.setMenuCategoryId(10L);
        sub.setSlug("corbalar");
        sub.setName("Çorbalar");

        MenuCategory main = new MenuCategory();
        main.setId(10L);
        main.setSlug("baslangiclar");
        main.setName("Başlangıçlar");

        MenuTag tag = new MenuTag();
        tag.setId(30L);
        tag.setSlug("vejetaryen");
        tag.setName("Vejetaryen");

        MenuAllergen allergen = new MenuAllergen();
        allergen.setId(40L);
        allergen.setSlug("gluten");
        allergen.setName("Gluten");

        when(menuCategoryService.loadSubCategoryMap(5L)).thenReturn(Map.of(20L, sub));
        when(menuCategoryService.loadCategoryMap(5L)).thenReturn(Map.of(10L, main));
        when(menuTaxonomyService.loadTagMap()).thenReturn(Map.of(30L, tag));
        when(menuTaxonomyService.loadAllergenMap()).thenReturn(Map.of(40L, allergen));
    }

    @Test
    void create_whenProductHasTaxonomy_thenResolveSlugsAndNames() {
        MenuProductDocumentMessage document = factory.create(product());

        assertThat(document.mainCategorySlug()).isEqualTo("baslangiclar");
        assertThat(document.subCategorySlug()).isEqualTo("corbalar");
        assertThat(document.tagSlugs()).containsExactly("vejetaryen");
        assertThat(document.tagNames()).containsExactly("Vejetaryen");
        assertThat(document.allergenSlugs()).containsExactly("gluten");
        assertThat(document.energyKcal()).isEqualByComparingTo("180");
    }

    @Test
    void create_whenProductHasNoRatings_thenOmitRatingAverage() {
        MenuProduct product = product();
        product.setRatingAvg(BigDecimal.ZERO);

        assertThat(factory.create(product).ratingAvg()).isNull();
    }

    @Test
    void create_whenUpdatedAtIsSet_thenConvertToUtcInstant() {
        MenuProduct product = product();
        LocalDateTime updatedAt = LocalDateTime.of(2026, 1, 2, 3, 4, 5);
        product.setUpdatedAt(updatedAt);

        assertThat(factory.create(product).updatedAt())
                .isEqualTo(updatedAt.toInstant(ZoneOffset.UTC));
    }

    @Test
    void createAll_whenBatchGiven_thenLoadTaxonomyOnlyOnce() {
        factory.createAll(List.of(product(), product(), product()));

        verify(menuCategoryService, times(1)).loadSubCategoryMap(5L);
        verify(menuTaxonomyService, times(1)).loadTagMap();
    }

    private static MenuProduct product() {
        NutritionFacts nutrition = new NutritionFacts();
        nutrition.setEnergyKcal(new BigDecimal("180"));

        MenuProduct product = MenuProduct.builder()
                .productId(1L)
                .menuId(5L)
                .name("Mercimek Çorbası")
                .description("Ev yapımı")
                .price(new BigDecimal("120.00"))
                .subCategoryId(20L)
                .tagIds(Set.of(30L))
                .allergenIds(Set.of(40L))
                .nutrition(nutrition)
                .build();
        product.setRatingAvg(new BigDecimal("4.50"));
        product.setRatingCount(12L);
        return product;
    }
}
