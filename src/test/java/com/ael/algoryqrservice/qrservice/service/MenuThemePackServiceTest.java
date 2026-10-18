package com.ael.algoryqrservice.qrservice.service;

import com.ael.algoryqrservice.qrservice.product.service.ProductImageStorageService;
import com.ael.algoryqrservice.qrservice.menu.service.MenuThemePackService;
import com.ael.algoryqrservice.qrservice.config.AppProperties;
import com.ael.algoryqrservice.qrservice.model.MenuTheme;
import com.ael.algoryqrservice.qrservice.repository.MenuThemeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MenuThemePackServiceTest {

    @Mock
    private MenuThemeRepository menuThemeRepository;

    @Mock
    private ProductImageStorageService productImageStorageService;

    private MenuThemePackService service;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties();
        properties.getMenu().setThemePackVersion("v1");
        service = new MenuThemePackService(menuThemeRepository, productImageStorageService, properties);
    }

    @Test
    void getThemePack_whenAssetsOnSeaweed_thenBuildsPublicUrls() {
        MenuTheme theme = MenuTheme.builder()
                .id(12L)
                .code("kahve-sokagi")
                .name("Köfteli Usta")
                .active(true)
                .build();
        when(menuThemeRepository.findByCodeIgnoreCase("kahve-sokagi")).thenReturn(Optional.of(theme));
        when(productImageStorageService.listObjectKeysUnderPrefix("themes/kahve-sokagi/v1/"))
                .thenReturn(List.of("themes/kahve-sokagi/v1/yerli-uretim.png"));
        when(productImageStorageService.buildPublicUrl("themes/kahve-sokagi/v1/yerli-uretim.png"))
                .thenReturn("https://cdn.example/qr-product-images/themes/kahve-sokagi/v1/yerli-uretim.png");

        var pack = service.getThemePack("kahve-sokagi");

        assertThat(pack.getCode()).isEqualTo("kahve-sokagi");
        assertThat(pack.getAssets()).hasSize(1);
        assertThat(pack.getAssets().get(0).getAssetKey()).isEqualTo("yerli-uretim");
        assertThat(pack.getAssets().get(0).getUrl()).contains("yerli-uretim.png");
    }
}
