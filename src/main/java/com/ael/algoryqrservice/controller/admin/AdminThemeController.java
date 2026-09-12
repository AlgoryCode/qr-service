package com.ael.algoryqrservice.controller.admin;

import com.ael.algoryqrservice.model.dto.ThemeDtos;
import com.ael.algoryqrservice.service.MenuThemeService;
import com.ael.algoryqrservice.util.DashboardSecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminThemeController {

    private final MenuThemeService menuThemeService;
    private final DashboardSecurityUtils dashboardSecurityUtils;

    /** Full theme catalog for admin assignment UI. */
    @GetMapping("/themes")
    public ResponseEntity<List<ThemeDtos.ThemeResponse>> listCatalog(
            @RequestParam(defaultValue = "false") boolean includeInactive
    ) {
        return ResponseEntity.ok(menuThemeService.listCatalog(includeInactive));
    }

    @GetMapping("/users/{userId}/themes")
    public ResponseEntity<List<ThemeDtos.ThemeResponse>> listUserThemes(@PathVariable Long userId) {
        return ResponseEntity.ok(menuThemeService.listAssignedThemes(userId));
    }

    /** Adds themes to the member's allowed set (idempotent). */
    @PostMapping("/users/{userId}/themes")
    public ResponseEntity<List<ThemeDtos.ThemeResponse>> assignThemes(
            @PathVariable Long userId,
            @Valid @RequestBody ThemeDtos.AssignThemesRequest request
    ) {
        Long adminId = dashboardSecurityUtils.getCurrentDashboardUser().getId();
        return ResponseEntity.ok(menuThemeService.assignThemes(userId, request.getThemeCodes(), adminId));
    }

    /** Replaces the member's allowed theme set. Empty list clears all assignments. */
    @PutMapping("/users/{userId}/themes")
    public ResponseEntity<List<ThemeDtos.ThemeResponse>> replaceThemes(
            @PathVariable Long userId,
            @Valid @RequestBody ThemeDtos.ReplaceThemesRequest request
    ) {
        Long adminId = dashboardSecurityUtils.getCurrentDashboardUser().getId();
        return ResponseEntity.ok(menuThemeService.replaceThemes(userId, request.getThemeCodes(), adminId));
    }

    @DeleteMapping("/users/{userId}/themes/{themeCode}")
    public ResponseEntity<List<ThemeDtos.ThemeResponse>> revokeTheme(
            @PathVariable Long userId,
            @PathVariable String themeCode
    ) {
        return ResponseEntity.ok(menuThemeService.revokeTheme(userId, themeCode));
    }
}
