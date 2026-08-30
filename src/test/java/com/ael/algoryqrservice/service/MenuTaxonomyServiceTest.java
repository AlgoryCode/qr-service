package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.MenuAllergen;
import com.ael.algoryqrservice.model.dto.TaxonomyDtos;
import com.ael.algoryqrservice.repository.MenuAllergenRepository;
import com.ael.algoryqrservice.repository.MenuTagRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MenuTaxonomyServiceTest {

    @Mock
    private MenuTagRepository menuTagRepository;
    @Mock
    private MenuAllergenRepository menuAllergenRepository;

    @InjectMocks
    private MenuTaxonomyService menuTaxonomyService;

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
