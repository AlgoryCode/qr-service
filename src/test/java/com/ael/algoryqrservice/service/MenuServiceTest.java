package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.config.AppProperties;
import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.MenuProduct;
import com.ael.algoryqrservice.model.Qr;
import com.ael.algoryqrservice.model.dto.QrRequest;
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

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
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
        details.put("products", List.of(
                Map.of(
                        "name", "Espresso",
                        "category", "İçecekler",
                        "price", "120",
                        "description", "Tek shot"
                )
        ));
        QrRequest request = new QrRequest();
        request.setDetails(details);

        when(menuRepository.save(any(Menu.class))).thenAnswer(invocation -> {
            Menu menu = invocation.getArgument(0);
            menu.setMenuId(99L);
            return menu;
        });
        when(menuCategoryService.findOrCreateRootCategory(eq(99L), eq("İçecekler"))).thenReturn(7L);
        when(menuProductRepository.save(any(MenuProduct.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Menu saved = menuService.createMenuForQr(qr, request);

        assertThat(saved.getBusinessName()).isEqualTo("Kafe İstanbul");
        assertThat(saved.getSlogan()).isEqualTo("Lezzetin adresi");
        assertThat(saved.getMenuId()).isEqualTo(99L);

        ArgumentCaptor<MenuProduct> productCaptor = ArgumentCaptor.forClass(MenuProduct.class);
        verify(menuProductRepository, times(1)).save(productCaptor.capture());
        MenuProduct product = productCaptor.getValue();
        assertThat(product.getMenuId()).isEqualTo(99L);
        assertThat(product.getName()).isEqualTo("Espresso");
        assertThat(product.getCategory()).isEqualTo("İçecekler");
        assertThat(product.getCategoryId()).isEqualTo(7L);
        assertThat(product.getPrice()).isEqualByComparingTo(new BigDecimal("120"));
        assertThat(product.getDescription()).isEqualTo("Tek shot");
    }
}
