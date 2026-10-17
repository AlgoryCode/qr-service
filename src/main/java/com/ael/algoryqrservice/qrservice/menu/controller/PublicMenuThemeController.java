package com.ael.algoryqrservice.qrservice.menu.controller;

import com.ael.algoryqrservice.qrservice.menu.dto.ThemeDtos;
import com.ael.algoryqrservice.qrservice.menu.service.MenuThemePackService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import com.ael.algoryqrservice.qrservice.model.Product;

/**
 * Public menu theme catalog and Seaweed-backed static pack assets (hero, badges, …).
 * Product photos remain on the public menu payload ({@code imageUrl}).
 */
@RestController
@RequestMapping("/menu/public/themes")
@RequiredArgsConstructor
public class PublicMenuThemeController {

    private final MenuThemePackService menuThemePackService;

    @GetMapping
    public ResponseEntity<List<ThemeDtos.PublicThemeSummaryResponse>> listThemes() {
        return ResponseEntity.ok(menuThemePackService.listPublicThemes());
    }

    @GetMapping("/{code}/assets")
    public ResponseEntity<ThemeDtos.ThemePackResponse> themeAssets(@PathVariable String code) {
        return ResponseEntity.ok(menuThemePackService.getThemePack(code));
    }
}
