package com.ael.algoryqrservice.qrservice.menu.service;

import com.ael.algoryqrservice.qrservice.config.AppProperties;
import com.ael.algoryqrservice.qrservice.exception.NotFoundException;
import com.ael.algoryqrservice.qrservice.model.MenuTheme;
import com.ael.algoryqrservice.qrservice.menu.dto.ThemeDtos;
import com.ael.algoryqrservice.qrservice.repository.MenuThemeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import com.ael.algoryqrservice.qrservice.product.service.ProductImageStorageService;

@Service
@RequiredArgsConstructor
public class MenuThemePackService {

    private final MenuThemeRepository menuThemeRepository;
    private final ProductImageStorageService productImageStorageService;
    private final AppProperties appProperties;

    @Transactional(readOnly = true)
    public List<ThemeDtos.PublicThemeSummaryResponse> listPublicThemes() {
        return menuThemeRepository.findAllByActiveTrueOrderBySortOrderAscIdAsc().stream()
                .map(theme -> ThemeDtos.PublicThemeSummaryResponse.builder()
                        .id(theme.getId())
                        .code(theme.getCode())
                        .name(theme.getName())
                        .sortOrder(theme.getSortOrder())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public ThemeDtos.ThemePackResponse getThemePack(String themeCode) {
        String code = normalizeCode(themeCode);
        MenuTheme theme = menuThemeRepository.findByCodeIgnoreCase(code)
                .filter(MenuTheme::isActive)
                .orElseThrow(() -> new NotFoundException("Tema bulunamadı"));

        String packVersion = appProperties.getMenu().getThemePackVersion();
        String prefix = themePackPrefix(code, packVersion);
        List<String> objectKeys = productImageStorageService.listObjectKeysUnderPrefix(prefix);

        List<ThemeDtos.ThemePackAssetResponse> assets = new ArrayList<>();
        for (String objectKey : objectKeys) {
            if (!objectKey.startsWith(prefix)) {
                continue;
            }
            String fileName = objectKey.substring(prefix.length());
            if (fileName.isBlank() || fileName.contains("/")) {
                continue;
            }
            assets.add(ThemeDtos.ThemePackAssetResponse.builder()
                    .assetKey(assetKeyFromFileName(fileName))
                    .fileName(fileName)
                    .objectKey(objectKey)
                    .url(productImageStorageService.buildPublicUrl(objectKey))
                    .build());
        }

        return ThemeDtos.ThemePackResponse.builder()
                .id(theme.getId())
                .code(theme.getCode())
                .name(theme.getName())
                .packVersion(packVersion)
                .assets(assets)
                .build();
    }

    static String themePackPrefix(String themeCode, String packVersion) {
        return "themes/" + themeCode + "/" + packVersion + "/";
    }

    static String assetKeyFromFileName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        return base.toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static String normalizeCode(String themeCode) {
        if (themeCode == null || themeCode.isBlank()) {
            throw new NotFoundException("Tema bulunamadı");
        }
        return themeCode.trim();
    }
}
