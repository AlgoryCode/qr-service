package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.config.AppProperties;
import com.ael.algoryqrservice.exception.ForbiddenException;
import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.MenuProduct;
import com.ael.algoryqrservice.model.Qr;
import com.ael.algoryqrservice.model.UrlMode;
import com.ael.algoryqrservice.model.dto.MenuDtos;
import com.ael.algoryqrservice.model.dto.QrRequest;
import com.ael.algoryqrservice.model.enums.NutritionBasis;
import com.ael.algoryqrservice.model.nutrition.NutritionFacts;
import com.ael.algoryqrservice.model.nutrition.NutritionNutrientEntry;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.repository.MenuProductRepository;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.repository.QrRepository;
import com.ael.algoryqrservice.util.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MenuServiceTest {

    @Mock
    private MenuRepository menuRepository;
    @Mock
    private MenuProductRepository menuProductRepository;
    @Mock
    private MenuCategoryService menuCategoryService;
    @Mock
    private MenuPublicAccessService menuPublicAccessService;
    @Mock
    private NutritionFactsService nutritionFactsService;
    @Mock
    private QrRepository qrRepository;
    @Mock
    private QrGenerationService qrGenerationService;
    @Mock
    private AppProperties appProperties;
    @Mock
    private SecurityUtils securityUtils;

    @InjectMocks
    private MenuService menuService;

    @Test
    void createMenuForQr_whenSloganAndProductsProvided_thenPersistAll() {
        Qr qr = Qr.builder().qrId(42L).userId(7L).build();
        Map<String, Object> details = new HashMap<>();
        details.put("themeId", "classic");
        details.put("businessName", "Kafe İstanbul");
        details.put("slogan", "Lezzetin adresi");
        details.put("urlMode", "ID");
        Map<String, Object> nutrition = Map.of(
                "basis", "PER_100G",
                "energyKj", 850,
                "energyKcal", 203,
                "fat", 10.5,
                "carbohydrate", 25,
                "fibre", 2.1,
                "protein", 8,
                "salt", 1.2
        );
        details.put("products", List.of(
                Map.of(
                        "name", "Espresso",
                        "category", "İçecekler",
                        "price", "120",
                        "description", "Tek shot",
                        "nutrition", nutrition
                )
        ));
        QrRequest request = new QrRequest();
        request.setDetails(details);
        NutritionFacts parsedNutrition = sampleNutrition();

        when(menuRepository.save(any(Menu.class))).thenAnswer(invocation -> {
            Menu menu = invocation.getArgument(0);
            menu.setMenuId(99L);
            return menu;
        });
        when(menuCategoryService.findOrCreateRootCategory(eq(99L), eq("İçecekler"))).thenReturn(7L);
        when(nutritionFactsService.parseFromRaw(nutrition)).thenReturn(parsedNutrition);
        when(menuProductRepository.save(any(MenuProduct.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Menu saved = menuService.createMenuForQr(qr, request);

        assertThat(saved.getBusinessName()).isEqualTo("Kafe İstanbul");
        assertThat(saved.getSlogan()).isEqualTo("Lezzetin adresi");
        assertThat(saved.getMenuId()).isEqualTo(99L);
        verify(menuPublicAccessService).syncForUser(7L);
        verify(nutritionFactsService).validateForCreate(parsedNutrition);

        ArgumentCaptor<MenuProduct> productCaptor = ArgumentCaptor.forClass(MenuProduct.class);
        verify(menuProductRepository, times(1)).save(productCaptor.capture());
        MenuProduct product = productCaptor.getValue();
        assertThat(product.getMenuId()).isEqualTo(99L);
        assertThat(product.getName()).isEqualTo("Espresso");
        assertThat(product.getCategory()).isEqualTo("İçecekler");
        assertThat(product.getCategoryId()).isEqualTo(7L);
        assertThat(product.getPrice()).isEqualByComparingTo(new BigDecimal("120"));
        assertThat(product.getDescription()).isEqualTo("Tek shot");
        assertThat(product.getNutrition()).isEqualTo(parsedNutrition);
    }

    @Test
    void createProduct_whenNutritionMissing_thenThrow() {
        Menu menu = Menu.builder().menuId(10L).userId(7L).build();
        when(menuRepository.findById(10L)).thenReturn(Optional.of(menu));
        when(securityUtils.getCurrentUser()).thenReturn(User.builder().id(7L).build());

        MenuDtos.MenuProductRequest request = MenuDtos.MenuProductRequest.builder()
                .name("Köfte")
                .build();

        org.mockito.Mockito.doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Besin ögesi bilgisi zorunludur"))
                .when(nutritionFactsService).validateForCreate(null);

        assertThatThrownBy(() -> menuService.createProduct(10L, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Besin");
    }

    @Test
    void patchProductNutrition_whenOnlySalt_thenMergeAndPreserveName() {
        NutritionFacts existing = sampleNutrition();
        existing.setOtherNutrients(List.of(
                NutritionNutrientEntry.builder().name("Omega-3").value(new BigDecimal("0.5")).unit("g").build()
        ));
        MenuProduct product = MenuProduct.builder()
                .productId(5L)
                .menuId(10L)
                .name("Köfte")
                .price(new BigDecimal("180"))
                .currency("TRY")
                .sortOrder(0)
                .available(true)
                .nutrition(existing)
                .build();
        Menu menu = Menu.builder().menuId(10L).userId(7L).build();
        NutritionFacts patch = NutritionFacts.builder().salt(new BigDecimal("1.4")).build();
        NutritionFacts merged = sampleNutrition();
        merged.setSalt(new BigDecimal("1.4"));
        merged.setOtherNutrients(existing.getOtherNutrients());

        when(menuProductRepository.findByProductIdAndDeletedFalse(5L)).thenReturn(Optional.of(product));
        when(menuRepository.findById(10L)).thenReturn(Optional.of(menu));
        when(securityUtils.getCurrentUser()).thenReturn(User.builder().id(7L).build());
        when(nutritionFactsService.merge(existing, patch)).thenReturn(merged);
        when(menuProductRepository.save(any(MenuProduct.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(menuCategoryService.loadCategoryMap(10L)).thenReturn(Map.of());

        MenuDtos.MenuProductResponse response = menuService.patchProductNutrition(5L, patch);

        assertThat(response.getName()).isEqualTo("Köfte");
        assertThat(response.getPrice()).isEqualByComparingTo("180");
        assertThat(response.getNutrition().getSalt()).isEqualByComparingTo("1.4");
        assertThat(response.getNutrition().getOtherNutrients()).hasSize(1);
        verify(nutritionFactsService).merge(existing, patch);
    }

    private NutritionFacts sampleNutrition() {
        return NutritionFacts.builder()
                .basis(NutritionBasis.PER_100G)
                .energyKj(new BigDecimal("850"))
                .energyKcal(new BigDecimal("203"))
                .fat(new BigDecimal("10.5"))
                .carbohydrate(new BigDecimal("25"))
                .fibre(new BigDecimal("2.1"))
                .protein(new BigDecimal("8"))
                .salt(new BigDecimal("1.2"))
                .build();
    }

    @Test
    void getPublicMenuByQrId_whenPublicAccessDisabled_thenThrowForbidden() {
        Menu menu = Menu.builder()
                .menuId(10L)
                .qrId(2L)
                .userId(7L)
                .themeId("classic")
                .businessName("Kafe")
                .urlMode(UrlMode.ID)
                .active(true)
                .publicAccessEnabled(false)
                .build();
        when(menuRepository.findByQrIdAndActiveTrueAndDeletedFalse(2L)).thenReturn(Optional.of(menu));

        assertThatThrownBy(() -> menuService.getPublicMenuByQrId(2L))
                .isInstanceOf(ForbiddenException.class)
                .satisfies(ex -> {
                    ForbiddenException forbidden = (ForbiddenException) ex;
                    assertThat(forbidden.getCode()).isEqualTo(ForbiddenException.MENU_OWNER_PACKAGE_INACTIVE);
                });
    }

    @Test
    void getPublicMenuByQrId_whenPublicAccessEnabled_thenReturnPublicMenu() {
        Menu menu = Menu.builder()
                .menuId(10L)
                .qrId(2L)
                .userId(7L)
                .themeId("classic")
                .businessName("Kafe")
                .urlMode(UrlMode.ID)
                .active(true)
                .publicAccessEnabled(true)
                .build();
        when(menuRepository.findByQrIdAndActiveTrueAndDeletedFalse(2L)).thenReturn(Optional.of(menu));
        when(appProperties.getUrl()).thenReturn("https://example.com");
        when(menuProductRepository.findByMenuIdAndDeletedFalseOrderBySortOrderAscProductIdAsc(10L))
                .thenReturn(List.of());
        when(menuCategoryService.listPublicCategoryTree(10L)).thenReturn(List.of());

        MenuDtos.PublicMenuResponse response = menuService.getPublicMenuByQrId(2L);

        assertThat(response.getThemeId()).isEqualTo("classic");
        assertThat(response.getMenu().getBusinessName()).isEqualTo("Kafe");
        assertThat(response.getProducts()).isEmpty();
    }
}
