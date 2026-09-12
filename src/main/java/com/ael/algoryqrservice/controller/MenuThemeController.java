package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.model.dto.ThemeDtos;
import com.ael.algoryqrservice.service.MenuThemeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Member appearance-screen theme APIs.
 * Kept separate from {@link MenuController} to avoid clashing with {@code /{menuId}} routes.
 */
@RestController
@RequestMapping("/menu")
@RequiredArgsConstructor
public class MenuThemeController {

    private final MenuThemeService menuThemeService;

    /**
     * Lists only themes assigned to the authenticated member ({@code tbl_user}).
     */
    @GetMapping("/themes")
    public ResponseEntity<List<ThemeDtos.ThemeResponse>> listMyThemes() {
        return ResponseEntity.ok(menuThemeService.listAllowedThemesForCurrentUser());
    }
}
