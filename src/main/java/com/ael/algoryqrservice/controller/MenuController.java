package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.model.dto.MenuDtos;
import com.ael.algoryqrservice.model.dto.TaxonomyDtos;
import com.ael.algoryqrservice.model.nutrition.NutritionFacts;
import com.ael.algoryqrservice.service.ChefAvatarService;
import com.ael.algoryqrservice.service.MenuService;
import com.ael.algoryqrservice.service.MenuTaxonomyService;
import com.ael.algoryqrservice.service.MenuProductRatingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/menu")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;
    private final MenuTaxonomyService menuTaxonomyService;
    private final MenuProductRatingService menuProductRatingService;
    private final ChefAvatarService chefAvatarService;

    @GetMapping("/public/id/{qrId}")
    public ResponseEntity<MenuDtos.PublicMenuResponse> getPublicMenuByQrId(@PathVariable Long qrId) {
        return ResponseEntity.ok(menuService.getPublicMenuByQrId(qrId));
    }

    @GetMapping("/public/{menuId}/products")
    public ResponseEntity<MenuDtos.MenuProductPageResponse> listPublicProducts(
            @PathVariable Long menuId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Boolean chefRecommended,
            @RequestParam(required = false) String tagSlug,
            @RequestParam(required = false) BigDecimal minRating,
            @RequestParam(required = false) Long subCategoryId,
            @RequestParam(required = false) Long mainCategoryId,
            @RequestParam(required = false) List<Long> tagIds,
            @RequestParam(required = false) String allergenSlug,
            @RequestParam(required = false) List<Long> allergenIds,
            @RequestParam(required = false) Integer servesPeople,
            @RequestParam(required = false) Integer servesPeopleMin,
            @RequestParam(required = false) Integer servesPeopleMax,
            @RequestParam(required = false) String q
    ) {
        return ResponseEntity.ok(menuService.listPublicProducts(
                menuId,
                page,
                size,
                chefRecommended,
                tagSlug,
                minRating,
                subCategoryId,
                mainCategoryId,
                tagIds,
                allergenSlug,
                allergenIds,
                servesPeople,
                servesPeopleMin,
                servesPeopleMax,
                q
        ));
    }

    @GetMapping("/public/{menuId}/product-facets")
    public ResponseEntity<MenuDtos.ProductFacetsResponse> listPublicProductFacets(
            @PathVariable Long menuId,
            @RequestParam(required = false) Boolean chefRecommended,
            @RequestParam(required = false) String tagSlug,
            @RequestParam(required = false) BigDecimal minRating,
            @RequestParam(required = false) Long subCategoryId,
            @RequestParam(required = false) Long mainCategoryId,
            @RequestParam(required = false) List<Long> tagIds,
            @RequestParam(required = false) String allergenSlug,
            @RequestParam(required = false) List<Long> allergenIds,
            @RequestParam(required = false) Integer servesPeople,
            @RequestParam(required = false) Integer servesPeopleMin,
            @RequestParam(required = false) Integer servesPeopleMax,
            @RequestParam(required = false) String q
    ) {
        return ResponseEntity.ok(menuService.listPublicProductFacets(
                menuId,
                chefRecommended,
                tagSlug,
                minRating,
                subCategoryId,
                mainCategoryId,
                tagIds,
                allergenSlug,
                allergenIds,
                servesPeople,
                servesPeopleMin,
                servesPeopleMax,
                q
        ));
    }

    @GetMapping("/public/{menuId}/products/{productId}/recommendations")
    public ResponseEntity<List<MenuDtos.MenuProductResponse>> listPublicRecommendations(
            @PathVariable Long menuId,
            @PathVariable Long productId,
            @RequestParam(defaultValue = "6") int limit
    ) {
        return ResponseEntity.ok(menuService.listPublicRecommendations(menuId, productId, limit));
    }

    @PostMapping("/public/{menuId}/products/{productId}/ratings")
    public ResponseEntity<MenuDtos.ProductRatingResponse> ratePublicProduct(
            @PathVariable Long menuId,
            @PathVariable Long productId,
            @Valid @RequestBody MenuDtos.ProductRatingRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.status(201).body(
                menuProductRatingService.rateProduct(menuId, productId, request, httpRequest)
        );
    }

    @GetMapping("/my/active")
    public ResponseEntity<List<MenuDtos.ActiveMenuSummary>> listMyActiveMenus() {
        return ResponseEntity.ok(menuService.listActiveMenusForCurrentUser());
    }

    @GetMapping("/by-qr/{qrId}")
    public ResponseEntity<MenuDtos.MenuProfileResponse> getMenuByQrId(@PathVariable Long qrId) {
        MenuDtos.MenuProfileResponse profile = menuService.getMenuProfileByQrId(qrId);
        if (profile == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(profile);
    }

    @GetMapping("/by-qr/{qrId}/products")
    public ResponseEntity<MenuDtos.MenuProductsByQrResponse> listProductsByQrId(@PathVariable Long qrId) {
        return ResponseEntity.ok(menuService.listProductsByQrId(qrId));
    }

    @GetMapping("/by-qr/{qrId}/categories")
    public ResponseEntity<MenuDtos.MenuCategoriesByQrResponse> listCategoriesByQrId(@PathVariable Long qrId) {
        return ResponseEntity.ok(menuService.listCategoriesByQrId(qrId));
    }

    @GetMapping("/chef-avatars")
    public ResponseEntity<List<MenuDtos.ChefAvatarItem>> listChefAvatars() {
        return ResponseEntity.ok(chefAvatarService.listAvatars());
    }

    @GetMapping("/{menuId}")
    public ResponseEntity<MenuDtos.MenuProfileResponse> getMenu(@PathVariable Long menuId) {
        return ResponseEntity.ok(menuService.getMenuProfile(menuId));
    }

    @PatchMapping("/{menuId}")
    public ResponseEntity<MenuDtos.MenuProfileResponse> updateMenu(
            @PathVariable Long menuId,
            @RequestBody MenuDtos.MenuUpdateRequest request
    ) throws Exception {
        return ResponseEntity.ok(menuService.updateMenu(menuId, request));
    }

    @GetMapping("/{menuId}/products")
    public ResponseEntity<MenuDtos.MenuProductPageResponse> listProducts(
            @PathVariable Long menuId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Boolean chefRecommended,
            @RequestParam(required = false) String tagSlug,
            @RequestParam(required = false) BigDecimal minRating,
            @RequestParam(required = false) Long subCategoryId,
            @RequestParam(required = false) Long mainCategoryId,
            @RequestParam(required = false) List<Long> tagIds,
            @RequestParam(required = false) String allergenSlug,
            @RequestParam(required = false) List<Long> allergenIds,
            @RequestParam(required = false) Integer servesPeople,
            @RequestParam(required = false) Integer servesPeopleMin,
            @RequestParam(required = false) Integer servesPeopleMax,
            @RequestParam(required = false) String q
    ) {
        return ResponseEntity.ok(menuService.listProducts(
                menuId,
                page,
                size,
                chefRecommended,
                tagSlug,
                minRating,
                subCategoryId,
                mainCategoryId,
                tagIds,
                allergenSlug,
                allergenIds,
                servesPeople,
                servesPeopleMin,
                servesPeopleMax,
                q
        ));
    }

    @PostMapping("/{menuId}/products")
    public ResponseEntity<MenuDtos.MenuProductResponse> createProduct(
            @PathVariable Long menuId,
            @RequestBody MenuDtos.MenuProductRequest request
    ) {
        return ResponseEntity.status(201).body(menuService.createProduct(menuId, request));
    }

    @PutMapping("/products/{productId}")
    public ResponseEntity<MenuDtos.MenuProductResponse> updateProduct(
            @PathVariable Long productId,
            @RequestBody MenuDtos.MenuProductRequest request
    ) {
        return ResponseEntity.ok(menuService.updateProduct(productId, request));
    }

    @PatchMapping("/products/{productId}/nutrition")
    public ResponseEntity<MenuDtos.MenuProductResponse> patchProductNutrition(
            @PathVariable Long productId,
            @RequestBody NutritionFacts request
    ) {
        return ResponseEntity.ok(menuService.patchProductNutrition(productId, request));
    }

    @DeleteMapping("/products/{productId}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long productId) {
        menuService.deleteProduct(productId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{menuId}/categories")
    public ResponseEntity<List<TaxonomyDtos.MainCategoryResponse>> listCategories(@PathVariable Long menuId) {
        menuService.getMenuProfile(menuId);
        return ResponseEntity.ok(menuTaxonomyService.listTaxonomy());
    }
}
