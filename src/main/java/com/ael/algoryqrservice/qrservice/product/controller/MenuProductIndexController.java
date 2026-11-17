package com.ael.algoryqrservice.qrservice.product.controller;

import com.ael.algoryqrservice.qrservice.security.FeatureKey;
import com.ael.algoryqrservice.qrservice.security.RequiresFeature;
import com.ael.algoryqrservice.qrservice.product.service.menuindex.MenuProductReindexService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/menu")
@RequiredArgsConstructor
public class MenuProductIndexController {

    private final MenuProductReindexService menuProductReindexService;

    @PostMapping("/{menuId}/search-index/reindex")
    @RequiresFeature(FeatureKey.QR_MENU)
    public ResponseEntity<MenuProductReindexService.ReindexSummary> reindexMenu(@PathVariable Long menuId) {
        return ResponseEntity.ok(menuProductReindexService.reindexMenu(menuId));
    }
}
