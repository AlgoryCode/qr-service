package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.MenuProduct;
import com.ael.algoryqrservice.repository.MenuProductRepository;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.service.entitlement.FeatureUsageSyncRegistry;
import com.ael.algoryqrservice.store.model.Merchant;
import com.ael.algoryqrservice.store.repository.MerchantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MenuCatalogCloneServiceTest {

    @Mock
    private MenuRepository menuRepository;
    @Mock
    private MenuProductRepository menuProductRepository;
    @Mock
    private MenuCategoryService menuCategoryService;
    @Mock
    private MenuProductPairingService menuProductPairingService;
    @Mock
    private MenuProductOptionService menuProductOptionService;
    @Mock
    private ProductImageStorageService productImageStorageService;
    @Mock
    private EntitlementService entitlementService;
    @Mock
    private FeatureUsageSyncRegistry usageSyncRegistry;
    @Mock
    private MerchantRepository merchantRepository;

    @InjectMocks
    private MenuCatalogCloneService menuCatalogCloneService;

    @Test
    void cloneInto_whenSourceHasProducts_thenCopyCatalog() {
        Menu target = Menu.builder().menuId(99L).userId(7L).build();
        Menu source = Menu.builder().menuId(12L).userId(7L).active(true).deleted(false).build();
        MenuProduct sourceProduct = MenuProduct.builder()
                .productId(100L)
                .menuId(12L)
                .name("Latte")
                .description("Sütlü kahve")
                .price(new BigDecimal("150"))
                .currency("TRY")
                .subCategoryId(3L)
                .sortOrder(0)
                .available(true)
                .chefRecommended(true)
                .tagIds(Set.of(8L))
                .allergenIds(Set.of(2L))
                .build();

        when(menuRepository.findById(12L)).thenReturn(Optional.of(source));
        when(menuProductRepository.findByMenuIdAndDeletedFalseOrderBySortOrderAscProductIdAsc(12L))
                .thenReturn(List.of(sourceProduct));
        when(menuProductRepository.saveAndFlush(any(MenuProduct.class))).thenAnswer(invocation -> {
            MenuProduct product = invocation.getArgument(0);
            product.setProductId(500L);
            return product;
        });
        when(menuCategoryService.cloneTaxonomyToMenu(12L, 99L))
                .thenReturn(new MenuCategoryService.TaxonomyCloneResult(Map.of(1L, 10L), Map.of(3L, 30L)));

        int copied = menuCatalogCloneService.cloneInto(target, 12L, 7L);

        assertThat(copied).isEqualTo(1);
        verify(entitlementService).assertMenuProductCreationAllowed(7L, 1);

        ArgumentCaptor<MenuProduct> productCaptor = ArgumentCaptor.forClass(MenuProduct.class);
        verify(menuProductRepository, times(1)).saveAndFlush(productCaptor.capture());
        MenuProduct saved = productCaptor.getValue();
        assertThat(saved.getMenuId()).isEqualTo(99L);
        assertThat(saved.getName()).isEqualTo("Latte");
        assertThat(saved.getSubCategoryId()).isEqualTo(30L);
        assertThat(saved.getTagIds()).containsExactly(8L);
        assertThat(saved.getAllergenIds()).containsExactly(2L);
        assertThat(saved.getRatingAvg()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.getRatingCount()).isZero();
        verify(menuProductPairingService).copyPairings(any(), eq(Map.of(1L, 10L)), eq(Map.of(3L, 30L)));
        verify(menuProductOptionService).copyOptions(Map.of(100L, 500L));
    }

    @Test
    void mirrorOptionsToStoreCatalog_whenMerchantHasMatchingProduct_thenOverwrite() {
        Merchant merchant = Merchant.builder().catalogMenuId(99L).build();
        when(merchantRepository.findByUserIdAndDeletedFalse(7L)).thenReturn(Optional.of(merchant));
        when(menuProductRepository.findByMenuIdAndNameIgnoreCaseAndDeletedFalse(99L, "Latte"))
                .thenReturn(List.of(MenuProduct.builder().productId(500L).name("Latte").build()));

        menuCatalogCloneService.mirrorOptionsToStoreCatalog(7L, 12L, 100L, "Latte");

        verify(menuProductOptionService).overwriteOnto(100L, List.of(500L));
    }

    @Test
    void mirrorOptionsToStoreCatalog_whenSourceIsStoreCatalog_thenSkip() {
        Merchant merchant = Merchant.builder().catalogMenuId(12L).build();
        when(merchantRepository.findByUserIdAndDeletedFalse(7L)).thenReturn(Optional.of(merchant));

        menuCatalogCloneService.mirrorOptionsToStoreCatalog(7L, 12L, 100L, "Latte");

        verifyNoInteractions(menuProductOptionService);
    }

    @Test
    void cloneInto_whenSourceMenuNotOwned_thenThrow() {
        Menu target = Menu.builder().menuId(99L).userId(7L).build();
        Menu source = Menu.builder().menuId(12L).userId(99L).active(true).deleted(false).build();
        when(menuRepository.findById(12L)).thenReturn(Optional.of(source));

        assertThatThrownBy(() -> menuCatalogCloneService.cloneInto(target, 12L, 7L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("erişim");
    }

    @Test
    void cloneInto_whenSourceMenuInactive_thenThrow() {
        Menu target = Menu.builder().menuId(99L).userId(7L).build();
        Menu source = Menu.builder().menuId(12L).userId(7L).active(false).deleted(false).build();
        when(menuRepository.findById(12L)).thenReturn(Optional.of(source));

        assertThatThrownBy(() -> menuCatalogCloneService.cloneInto(target, 12L, 7L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("aktif");
    }

    @Test
    void cloneInto_whenSourceHasNoProducts_thenSkipTaxonomyClone() {
        Menu target = Menu.builder().menuId(99L).userId(7L).build();
        Menu source = Menu.builder().menuId(12L).userId(7L).active(true).deleted(false).build();
        when(menuRepository.findById(12L)).thenReturn(Optional.of(source));
        when(menuProductRepository.findByMenuIdAndDeletedFalseOrderBySortOrderAscProductIdAsc(12L))
                .thenReturn(List.of());

        assertThat(menuCatalogCloneService.cloneInto(target, 12L, 7L)).isZero();
        verifyNoInteractions(menuCategoryService, menuProductPairingService, menuProductOptionService, usageSyncRegistry);
    }

    @Test
    void cloneInto_whenSubCategoryMissingInTarget_thenThrow() {
        Menu target = Menu.builder().menuId(99L).userId(7L).build();
        Menu source = Menu.builder().menuId(12L).userId(7L).active(true).deleted(false).build();
        MenuProduct orphan = MenuProduct.builder()
                .productId(100L)
                .menuId(12L)
                .name("Latte")
                .price(new BigDecimal("150"))
                .subCategoryId(404L)
                .build();

        when(menuRepository.findById(12L)).thenReturn(Optional.of(source));
        when(menuProductRepository.findByMenuIdAndDeletedFalseOrderBySortOrderAscProductIdAsc(12L))
                .thenReturn(List.of(orphan));
        when(menuCategoryService.cloneTaxonomyToMenu(12L, 99L))
                .thenReturn(new MenuCategoryService.TaxonomyCloneResult(Map.of(), Map.of()));

        assertThatThrownBy(() -> menuCatalogCloneService.cloneInto(target, 12L, 7L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Latte");
    }

    @Test
    void countCloneableProducts_whenSourceOwned_thenReturnCount() {
        Menu source = Menu.builder().menuId(12L).userId(7L).active(true).deleted(false).build();
        when(menuRepository.findById(12L)).thenReturn(Optional.of(source));
        when(menuProductRepository.findByMenuIdAndDeletedFalseOrderBySortOrderAscProductIdAsc(12L))
                .thenReturn(List.of(MenuProduct.builder().productId(1L).build()));

        assertThat(menuCatalogCloneService.countCloneableProducts(12L, 7L)).isEqualTo(1);
    }
}
