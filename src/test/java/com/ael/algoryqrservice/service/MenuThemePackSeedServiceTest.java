package com.ael.algoryqrservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.IOException;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MenuThemePackSeedServiceTest {

    @Mock
    private ProductImageStorageService productImageStorageService;

    @Mock
    private ResourcePatternResolver resourcePatternResolver;

    private MenuThemePackSeedService service;

    @BeforeEach
    void setUp() {
        service = new MenuThemePackSeedService(productImageStorageService, resourcePatternResolver);
    }

    @Test
    void contentTypeForPath_whenCss_thenTextCss() {
        assertThat(service.contentTypeForPath("themes/luxury/v1/theme.css")).isEqualTo("text/css");
        assertThat(service.contentTypeForPath("themes/elixir/v1/hero.svg")).isEqualTo("image/svg+xml");
    }

    @Test
    void objectKeyFor_whenClasspathUri_thenThemesRelativePath() throws Exception {
        var resource = new ByteArrayResource("body{}".getBytes()) {
            @Override
            public String getFilename() {
                return "theme.css";
            }

            @Override
            public URI getURI() {
                return URI.create("file:/app/themes/luxury/v1/theme.css");
            }
        };
        assertThat(service.objectKeyFor(resource)).isEqualTo("themes/luxury/v1/theme.css");
    }

    @Test
    void seedIfMissing_whenObjectExists_thenSkipsUpload() throws IOException {
        var resource = new ByteArrayResource(".x{}".getBytes()) {
            @Override
            public String getFilename() {
                return "theme.css";
            }

            @Override
            public URI getURI() {
                return URI.create("file:/app/themes/luxury/v1/theme.css");
            }
        };
        when(resourcePatternResolver.getResources("classpath*:themes/**/*.*")).thenReturn(new org.springframework.core.io.Resource[]{resource});
        when(productImageStorageService.exists("themes/luxury/v1/theme.css")).thenReturn(true);

        service.seedIfMissing();

        verify(productImageStorageService, never()).uploadSeedBytes(any(), any(), any());
    }

    @Test
    void seedIfMissing_whenMissing_thenUploadsCss() throws IOException {
        byte[] body = ".luxury-menu{}".getBytes();
        var resource = new ByteArrayResource(body) {
            @Override
            public String getFilename() {
                return "theme.css";
            }

            @Override
            public URI getURI() {
                return URI.create("file:/app/themes/luxury/v1/theme.css");
            }
        };
        when(resourcePatternResolver.getResources("classpath*:themes/**/*.*")).thenReturn(new org.springframework.core.io.Resource[]{resource});
        when(productImageStorageService.exists("themes/luxury/v1/theme.css")).thenReturn(false);

        service.seedIfMissing();

        verify(productImageStorageService).uploadSeedBytes(eq("themes/luxury/v1/theme.css"), eq(body), eq("text/css"));
    }
}
