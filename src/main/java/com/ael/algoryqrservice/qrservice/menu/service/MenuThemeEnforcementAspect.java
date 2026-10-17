package com.ael.algoryqrservice.qrservice.menu.service;

import com.ael.algoryqrservice.qrservice.catalog.CatalogThemes;
import com.ael.algoryqrservice.qrservice.exception.NotFoundException;
import com.ael.algoryqrservice.qrservice.model.Menu;
import com.ael.algoryqrservice.qrservice.qr.model.Qr;
import com.ael.algoryqrservice.qrservice.menu.dto.MenuDtos;
import com.ael.algoryqrservice.qrservice.qr.dto.QrRequest;
import com.ael.algoryqrservice.qrservice.repository.MenuRepository;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;
import com.ael.algoryqrservice.qrservice.menu.service.MenuService;

/**
 * Enforces member theme assignments for preset themes on menu create/update.
 * Custom themes ({@code custom-*}) remain gated by {@code CUSTOM_DESIGN_OWNER} inside {@link MenuService}.
 */
@Aspect
@Component
@RequiredArgsConstructor
public class MenuThemeEnforcementAspect {

    private final MenuThemeService menuThemeService;
    private final MenuRepository menuRepository;

    @Before("execution(* com.ael.algoryqrservice.qrservice.menu.service.MenuService.createMenuForQr(..)) && args(qr, request)")
    public void enforceOnCreate(Qr qr, QrRequest request) {
        if (qr == null || request == null || request.getDetails() == null) {
            return;
        }
        Object raw = request.getDetails().get("themeId");
        if (raw == null) {
            return;
        }
        String themeId = raw.toString().trim();
        if (themeId.isEmpty() || CatalogThemes.isCustomTheme(themeId)) {
            return;
        }
        // Teması hiç atanmamış eski deneme hesapları için varsayılan temayı verir.
        menuThemeService.ensureDefaultThemeAssigned(qr.getUserId());
        menuThemeService.requireAssignedTheme(qr.getUserId(), themeId);
    }

    @Before("execution(* com.ael.algoryqrservice.qrservice.menu.service.MenuService.updateMenu(..)) && args(menuId, request)")
    public void enforceOnUpdate(Long menuId, MenuDtos.MenuUpdateRequest request) {
        if (request == null || request.getThemeId() == null || request.getThemeId().isBlank()) {
            return;
        }
        String themeId = request.getThemeId().trim();
        if (CatalogThemes.isCustomTheme(themeId)) {
            return;
        }
        Menu menu = menuRepository.findById(menuId)
                .orElseThrow(() -> new NotFoundException("Menü bulunamadı"));
        menuThemeService.requireAssignedTheme(menu.getUserId(), themeId);
    }
}
