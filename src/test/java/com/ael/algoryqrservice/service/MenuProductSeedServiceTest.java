package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.config.AppProperties;
import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.MenuProduct;
import com.ael.algoryqrservice.model.MenuSubCategory;
import com.ael.algoryqrservice.model.MenuTag;
import com.ael.algoryqrservice.model.dto.MenuProductSeedDtos;
import com.ael.algoryqrservice.model.enums.NutritionBasis;
import com.ael.algoryqrservice.model.nutrition.NutritionFacts;
import com.ael.algoryqrservice.repository.MenuAllergenRepository;
import com.ael.algoryqrservice.repository.MenuProductRepository;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.repository.MenuSubCategoryRepository;
import com.ael.algoryqrservice.repository.MenuTagRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MenuProductSeedServiceTest {

    @Mock
    private MenuRepository menuRepository;
    @Mock
    private MenuProductRepository menuProductRepository;
    @Mock
    private MenuSubCategoryRepository menuSubCategoryRepository;
    @Mock
    private MenuTagRepository menuTagRepository;
    @Mock
    private MenuAllergenRepository menuAllergenRepository;
    @Mock
    private NutritionFactsService nutritionFactsService;
    @Mock
    private ObjectMapper objectMapper;

    private AppProperties appProperties;
    private MenuProductSeedService service;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        appProperties.getSeed().getMenuProducts().setEnabled(true);
        appProperties.getSeed().getMenuProducts().setOnlyIfEmpty(true);
        service = new MenuProductSeedService(
                menuRepository,
                menuProductRepository,
                menuSubCategoryRepository,
                menuTagRepository,
                menuAllergenRepository,
                nutritionFactsService,
                new ServesPeopleSupport(),
                objectMapper,
                appProperties
        );
    }

    @Test
    void seedDocument_whenEmptyMenu_thenCreatesProductWithTags() {
        NutritionFacts nutrition = NutritionFacts.builder()
                .basis(NutritionBasis.PER_100G)
                .energyKj(new BigDecimal("100"))
                .energyKcal(new BigDecimal("24"))
                .fat(new BigDecimal("1"))
                .carbohydrate(new BigDecimal("2"))
                .fibre(new BigDecimal("0"))
                .protein(new BigDecimal("1"))
                .salt(new BigDecimal("0.1"))
                .build();
        MenuProductSeedDtos.Document document = MenuProductSeedDtos.Document.builder()
                .version(1)
                .menuId(4L)
                .products(List.of(MenuProductSeedDtos.ProductSeed.builder()
                        .name("Türk Kahvesi")
                        .subCategorySlug("sicak_icecekler")
                        .tagSlugs(List.of("seker_ilavesiz"))
                        .price(new BigDecimal("90"))
                        .sortOrder(1)
                        .nutrition(nutrition)
                        .build()))
                .build();

        when(menuRepository.findById(4L)).thenReturn(Optional.of(Menu.builder().menuId(4L).deleted(false).build()));
        when(menuProductRepository.countByMenuIdAndDeletedFalse(4L)).thenReturn(0L);
        when(menuProductRepository.existsByMenuIdAndNameIgnoreCaseAndDeletedFalse(4L, "Türk Kahvesi")).thenReturn(false);
        when(menuSubCategoryRepository.findByMenuIdAndSlugAndDeletedFalse(4L, "sicak_icecekler"))
                .thenReturn(Optional.of(MenuSubCategory.builder().id(1L).slug("sicak_icecekler").menuCategoryId(1L).name("Sıcak").sortOrder(1).build()));
        when(menuTagRepository.findBySlugAndDeletedFalse("seker_ilavesiz"))
                .thenReturn(Optional.of(MenuTag.builder().id(5L).slug("seker_ilavesiz").name("Şeker İlavesiz").sortOrder(5).build()));
        when(menuProductRepository.save(any(MenuProduct.class))).thenAnswer(invocation -> invocation.getArgument(0));

        int created = service.seedDocument(document, true);

        assertThat(created).isEqualTo(1);
        ArgumentCaptor<MenuProduct> captor = ArgumentCaptor.forClass(MenuProduct.class);
        verify(menuProductRepository).save(captor.capture());
        assertThat(captor.getValue().getSubCategoryId()).isEqualTo(1L);
        assertThat(captor.getValue().getTagIds()).containsExactly(5L);
        assertThat(captor.getValue().getServesPeopleMin()).isEqualTo(1);
        assertThat(captor.getValue().getServesPeopleMax()).isEqualTo(1);
        verify(nutritionFactsService).validateForCreate(nutrition);
    }

    @Test
    void seedDocument_whenMenuDeleted_thenSkip() {
        when(menuRepository.findById(4L)).thenReturn(Optional.of(Menu.builder().menuId(4L).deleted(true).build()));

        int created = service.seedDocument(
                MenuProductSeedDtos.Document.builder().menuId(4L).products(List.of()).build(),
                false
        );

        assertThat(created).isZero();
        verify(menuProductRepository, never()).save(any());
    }

    @Test
    void seedDocument_whenOnlyIfEmptyAndHasProducts_thenSkip() {
        when(menuRepository.findById(4L)).thenReturn(Optional.of(Menu.builder().menuId(4L).deleted(false).build()));
        when(menuProductRepository.countByMenuIdAndDeletedFalse(4L)).thenReturn(3L);

        int created = service.seedDocument(
                MenuProductSeedDtos.Document.builder().menuId(4L).products(List.of()).build(),
                true
        );

        assertThat(created).isZero();
        verify(menuProductRepository, never()).save(any());
    }
}
