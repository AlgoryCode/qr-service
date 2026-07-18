package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.dto.MenuDtos;
import com.ael.algoryqrservice.model.nutrition.NutritionFacts;
import com.ael.algoryqrservice.catalog.CatalogScopes;
import com.ael.algoryqrservice.security.RequiresProductScope;
import com.ael.algoryqrservice.service.MenuCategoryService;
import com.ael.algoryqrservice.service.MenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/menu")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;
    private final MenuCategoryService menuCategoryService;

    @GetMapping("/public/id/{qrId}")
    public ResponseEntity<MenuDtos.PublicMenuResponse> getPublicMenuByQrId(@PathVariable Long qrId) {
        return ResponseEntity.ok(menuService.getPublicMenuByQrId(qrId));
    }

    @GetMapping("/public/slug/{slug}")
    public ResponseEntity<MenuDtos.PublicMenuResponse> getPublicMenuBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(menuService.getPublicMenuBySlug(slug));
    }

    @GetMapping("/slug-available")
    public ResponseEntity<MenuDtos.SlugAvailabilityResponse> checkSlugAvailability(
            @RequestParam String slug,
            @RequestParam(required = false) Long excludeMenuId
    ) {
        return ResponseEntity.ok(menuService.checkSlugAvailability(slug, excludeMenuId));
    }

    @GetMapping("/by-qr/{qrId}")
    @RequiresProductScope(CatalogScopes.QR_MENU_OWNER)
    public ResponseEntity<MenuDtos.MenuProfileResponse> getMenuByQrId(@PathVariable Long qrId) {
        Menu menu = menuService.findByQrId(qrId);
        if (menu == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(menuService.getMenuProfile(menu.getMenuId()));
    }

    @GetMapping("/{menuId}")
    @RequiresProductScope(CatalogScopes.QR_MENU_OWNER)
    public ResponseEntity<MenuDtos.MenuProfileResponse> getMenu(@PathVariable Long menuId) {
        return ResponseEntity.ok(menuService.getMenuProfile(menuId));
    }

    @PatchMapping("/{menuId}")
    @RequiresProductScope(CatalogScopes.QR_MENU_OWNER)
    public ResponseEntity<MenuDtos.MenuProfileResponse> updateMenu(
            @PathVariable Long menuId,
            @RequestBody MenuDtos.MenuUpdateRequest request
    ) throws Exception {
        return ResponseEntity.ok(menuService.updateMenu(menuId, request));
    }

    @GetMapping("/{menuId}/products")
    @RequiresProductScope(CatalogScopes.QR_MENU_OWNER)
    public ResponseEntity<List<MenuDtos.MenuProductResponse>> listProducts(@PathVariable Long menuId) {
        return ResponseEntity.ok(menuService.listProducts(menuId));
    }

    @PostMapping("/{menuId}/products")
    @RequiresProductScope(CatalogScopes.QR_MENU_OWNER)
    public ResponseEntity<MenuDtos.MenuProductResponse> createProduct(
            @PathVariable Long menuId,
            @RequestBody MenuDtos.MenuProductRequest request
    ) {
        return ResponseEntity.status(201).body(menuService.createProduct(menuId, request));
    }

    @PutMapping("/products/{productId}")
    @RequiresProductScope(CatalogScopes.QR_MENU_OWNER)
    public ResponseEntity<MenuDtos.MenuProductResponse> updateProduct(
            @PathVariable Long productId,
            @RequestBody MenuDtos.MenuProductRequest request
    ) {
        return ResponseEntity.ok(menuService.updateProduct(productId, request));
    }

    @PatchMapping("/products/{productId}/nutrition")
    @RequiresProductScope(CatalogScopes.QR_MENU_OWNER)
    public ResponseEntity<MenuDtos.MenuProductResponse> patchProductNutrition(
            @PathVariable Long productId,
            @RequestBody NutritionFacts request
    ) {
        return ResponseEntity.ok(menuService.patchProductNutrition(productId, request));
    }

    @DeleteMapping("/products/{productId}")
    @RequiresProductScope(CatalogScopes.QR_MENU_OWNER)
    public ResponseEntity<Void> deleteProduct(@PathVariable Long productId) {
        menuService.deleteProduct(productId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{menuId}/categories")
    @RequiresProductScope(CatalogScopes.QR_MENU_OWNER)
    public ResponseEntity<List<MenuDtos.MenuCategoryResponse>> listCategories(@PathVariable Long menuId) {
        return ResponseEntity.ok(menuCategoryService.listCategoryTree(menuId));
    }

    @PostMapping("/{menuId}/categories")
    @RequiresProductScope(CatalogScopes.QR_MENU_OWNER)
    public ResponseEntity<MenuDtos.MenuCategoryResponse> createCategory(
            @PathVariable Long menuId,
            @RequestBody MenuDtos.MenuCategoryRequest request
    ) {
        return ResponseEntity.status(201).body(menuCategoryService.createCategory(menuId, request));
    }

    @PutMapping("/categories/{categoryId}")
    @RequiresProductScope(CatalogScopes.QR_MENU_OWNER)
    public ResponseEntity<MenuDtos.MenuCategoryResponse> updateCategory(
            @PathVariable Long categoryId,
            @RequestBody MenuDtos.MenuCategoryUpdateRequest request
    ) {
        return ResponseEntity.ok(menuCategoryService.updateCategory(categoryId, request));
    }

    @DeleteMapping("/categories/{categoryId}")
    @RequiresProductScope(CatalogScopes.QR_MENU_OWNER)
    public ResponseEntity<Void> deleteCategory(@PathVariable Long categoryId) {
        menuCategoryService.deleteCategory(categoryId);
        return ResponseEntity.noContent().build();
    }
}
