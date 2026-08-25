package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.MainCategory;
import com.ael.algoryqrservice.model.MenuProduct;
import com.ael.algoryqrservice.model.MenuProductPairing;
import com.ael.algoryqrservice.model.SubCategory;
import com.ael.algoryqrservice.model.dto.MenuDtos;
import com.ael.algoryqrservice.repository.MenuProductPairingRepository;
import com.ael.algoryqrservice.repository.MenuProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MenuProductPairingServiceTest {

    @Mock
    private MenuProductPairingRepository menuProductPairingRepository;
    @Mock
    private MenuProductRepository menuProductRepository;
    @Mock
    private MenuTaxonomyService menuTaxonomyService;

    @InjectMocks
    private MenuProductPairingService menuProductPairingService;

    @Test
    void replace_whenSelfProductId_thenSkipSelfAndSaveOthers() {
        when(menuProductRepository.findByProductIdInAndDeletedFalse(List.of(20L)))
                .thenReturn(List.of(MenuProduct.builder().productId(20L).menuId(8L).build()));
        when(menuTaxonomyService.requireMainCategory(3L))
                .thenReturn(MainCategory.builder().id(3L).slug("atistirmaliklar").name("Atıştırmalıklar").sortOrder(1).build());
        when(menuTaxonomyService.requireSubCategory(9L))
                .thenReturn(SubCategory.builder().id(9L).mainCategoryId(3L).slug("soslar").name("Soslar").sortOrder(1).build());

        menuProductPairingService.replace(10L, 8L, MenuDtos.MenuProductPairingsRequest.builder()
                .productIds(List.of(10L, 20L))
                .mainCategoryIds(List.of(3L))
                .subCategoryIds(List.of(9L))
                .build());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MenuProductPairing>> captor = ArgumentCaptor.forClass(List.class);
        verify(menuProductPairingRepository).deleteByProductId(10L);
        verify(menuProductPairingRepository).saveAll(captor.capture());
        List<MenuProductPairing> saved = captor.getValue();
        assertThat(saved).hasSize(3);
        assertThat(saved.stream().map(MenuProductPairing::getTargetProductId)).containsExactly(20L, null, null);
        assertThat(saved.stream().map(MenuProductPairing::getTargetMainCategoryId)).contains(3L);
        assertThat(saved.stream().map(MenuProductPairing::getTargetSubCategoryId)).contains(9L);
    }

    @Test
    void replace_whenTargetFromOtherMenu_thenThrow() {
        when(menuProductRepository.findByProductIdInAndDeletedFalse(List.of(20L)))
                .thenReturn(List.of(MenuProduct.builder().productId(20L).menuId(99L).build()));

        assertThatThrownBy(() -> menuProductPairingService.replace(10L, 8L,
                MenuDtos.MenuProductPairingsRequest.builder().productIds(List.of(20L)).build()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("aynı menüde");
        verify(menuProductPairingRepository, never()).saveAll(any());
    }

    @Test
    void copyPairings_whenSourceHasTargets_thenRemapProductIds() {
        when(menuProductPairingRepository.findByProductIdInOrderBySortOrderAscIdAsc(any()))
                .thenReturn(List.of(
                        MenuProductPairing.builder().productId(1L).targetProductId(2L).sortOrder(0).build(),
                        MenuProductPairing.builder().productId(1L).targetSubCategoryId(9L).sortOrder(1).build()
                ));

        menuProductPairingService.copyPairings(Map.of(1L, 101L, 2L, 102L));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MenuProductPairing>> captor = ArgumentCaptor.forClass(List.class);
        verify(menuProductPairingRepository).saveAll(captor.capture());
        List<MenuProductPairing> copies = captor.getValue();
        assertThat(copies).hasSize(2);
        assertThat(copies.get(0).getProductId()).isEqualTo(101L);
        assertThat(copies.get(0).getTargetProductId()).isEqualTo(102L);
        assertThat(copies.get(1).getTargetSubCategoryId()).isEqualTo(9L);
    }
}
