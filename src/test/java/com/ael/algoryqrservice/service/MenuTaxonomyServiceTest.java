package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.MainCategory;
import com.ael.algoryqrservice.model.MenuAllergen;
import com.ael.algoryqrservice.model.SubCategory;
import com.ael.algoryqrservice.model.dto.TaxonomyDtos;
import com.ael.algoryqrservice.repository.MainCategoryRepository;
import com.ael.algoryqrservice.repository.MenuAllergenRepository;
import com.ael.algoryqrservice.repository.MenuProductRepository;
import com.ael.algoryqrservice.repository.MenuTagRepository;
import com.ael.algoryqrservice.repository.SubCategoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MenuTaxonomyServiceTest {

    @Mock
    private MainCategoryRepository mainCategoryRepository;
    @Mock
    private SubCategoryRepository subCategoryRepository;
    @Mock
    private MenuTagRepository menuTagRepository;
    @Mock
    private MenuAllergenRepository menuAllergenRepository;
    @Mock
    private MenuProductRepository menuProductRepository;

    @InjectMocks
    private MenuTaxonomyService menuTaxonomyService;

    @Test
    void listTaxonomy_whenMainsAndSubs_thenNestByMainId() {
        when(mainCategoryRepository.findByDeletedFalseOrderBySortOrderAscIdAsc()).thenReturn(List.of(
                MainCategory.builder().id(1L).slug("icecekler").name("İçecekler").sortOrder(1).build(),
                MainCategory.builder().id(10L).slug("tatlilar").name("Tatlılar").sortOrder(10).build()
        ));
        when(subCategoryRepository.findByDeletedFalseOrderBySortOrderAscIdAsc()).thenReturn(List.of(
                SubCategory.builder().id(1L).mainCategoryId(1L).slug("sicak_icecekler").name("Sıcak").sortOrder(1).build(),
                SubCategory.builder().id(4L).mainCategoryId(10L).slug("sutlu_tatlilar").name("Sütlü").sortOrder(1).build(),
                SubCategory.builder().id(2L).mainCategoryId(1L).slug("soguk_icecekler").name("Soğuk").sortOrder(2).build()
        ));

        List<TaxonomyDtos.MainCategoryResponse> taxonomy = menuTaxonomyService.listTaxonomy();

        assertThat(taxonomy).hasSize(2);
        assertThat(taxonomy.getFirst().getSubs()).extracting(TaxonomyDtos.SubCategoryResponse::getId)
                .containsExactly(1L, 2L);
        assertThat(taxonomy.get(1).getSubs()).extracting(TaxonomyDtos.SubCategoryResponse::getSlug)
                .containsExactly("sutlu_tatlilar");
    }

    @Test
    void requireSubCategory_whenNull_thenBadRequest() {
        assertThatThrownBy(() -> menuTaxonomyService.requireSubCategory(null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("subCategoryId");
    }

    @Test
    void resolveSubCategoryBySlug_whenExists_thenReturn() {
        SubCategory sub = SubCategory.builder().id(4L).mainCategoryId(10L).slug("sutlu_tatlilar").name("Sütlü").sortOrder(1).build();
        when(subCategoryRepository.findBySlugAndDeletedFalse("sutlu_tatlilar")).thenReturn(Optional.of(sub));

        assertThat(menuTaxonomyService.resolveSubCategoryBySlug("sutlu_tatlilar").getId()).isEqualTo(4L);
    }

    @Test
    void createSub_whenSlugExists_thenBadRequest() {
        when(mainCategoryRepository.findByIdAndDeletedFalse(1L))
                .thenReturn(Optional.of(MainCategory.builder().id(1L).slug("icecekler").name("İçecekler").sortOrder(1).build()));
        when(subCategoryRepository.existsBySlugAndDeletedFalse("sicak_icecekler")).thenReturn(true);

        TaxonomyDtos.SubCategoryRequest request = TaxonomyDtos.SubCategoryRequest.builder()
                .mainCategoryId(1L)
                .slug("sicak_icecekler")
                .name("Sıcak")
                .build();

        assertThatThrownBy(() -> menuTaxonomyService.createSub(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("slug");
        verify(subCategoryRepository, never()).save(any());
    }

    @Test
    void listAllergens_whenPresent_thenReturnOrdered() {
        when(menuAllergenRepository.findByDeletedFalseOrderBySortOrderAscIdAsc()).thenReturn(List.of(
                MenuAllergen.builder().id(1L).slug("gluten").name("Glüten içeren tahıllar").sortOrder(1).build(),
                MenuAllergen.builder().id(7L).slug("sut").name("Süt").sortOrder(7).build()
        ));

        List<TaxonomyDtos.AllergenResponse> allergens = menuTaxonomyService.listAllergens();

        assertThat(allergens).hasSize(2);
        assertThat(allergens.getFirst().getSlug()).isEqualTo("gluten");
        assertThat(allergens.get(1).getName()).isEqualTo("Süt");
    }

    @Test
    void requireAllergens_whenInvalidId_thenBadRequest() {
        when(menuAllergenRepository.findByIdInAndDeletedFalse(List.of(99L))).thenReturn(List.of());

        assertThatThrownBy(() -> menuTaxonomyService.requireAllergens(List.of(99L)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("allergen");
    }

    @Test
    void createAllergen_whenValid_thenPersist() {
        when(menuAllergenRepository.existsBySlugAndDeletedFalse("gluten")).thenReturn(false);
        when(menuAllergenRepository.existsById(1L)).thenReturn(false);
        when(menuAllergenRepository.save(any(MenuAllergen.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaxonomyDtos.AllergenResponse response = menuTaxonomyService.createAllergen(
                TaxonomyDtos.AllergenRequest.builder()
                        .id(1L)
                        .slug("gluten")
                        .name("Glüten içeren tahıllar")
                        .sortOrder(1)
                        .build()
        );

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getSlug()).isEqualTo("gluten");
        verify(menuAllergenRepository).save(any(MenuAllergen.class));
    }
}
