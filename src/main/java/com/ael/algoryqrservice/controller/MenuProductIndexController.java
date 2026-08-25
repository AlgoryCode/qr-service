package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.catalog.CatalogScopes;
import com.ael.algoryqrservice.security.RequiresProductScope;
import com.ael.algoryqrservice.service.menuindex.MenuProductReindexService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/menu")
@RequiredArgsConstructor
public class MenuProductIndexController {

    private final MenuProductReindexService menuProductReindexService;

    @PostMapping("/{menuId}/search-index/reindex")
    @RequiresProductScope(CatalogScopes.QR_MENU_OWNER)
    public ResponseEntity<MenuProductReindexService.ReindexSummary> reindexMenu(@PathVariable Long menuId) {
        return ResponseEntity.ok(menuProductReindexService.reindexMenu(menuId));
    }
}
