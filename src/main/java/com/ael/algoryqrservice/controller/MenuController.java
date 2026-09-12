package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.catalog.CatalogScopes;
import com.ael.algoryqrservice.model.dto.MenuDtos;
import com.ael.algoryqrservice.model.dto.TaxonomyDtos;
import com.ael.algoryqrservice.model.nutrition.NutritionFacts;
import com.ael.algoryqrservice.security.RequiresProductScope;
import com.ael.algoryqrservice.service.ChefAvatarService;
import com.ael.algoryqrservice.service.MenuFeedbackService;
import com.ael.algoryqrservice.service.MenuReservationService;
import com.ael.algoryqrservice.service.MenuService;
import com.ael.algoryqrservice.service.MenuProductRatingService;
import com.ael.algoryqrservice.service.MenuRatingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/menu")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;
    private final MenuProductRatingService menuProductRatingService;
    private final MenuRatingService menuRatingService;
    private final MenuFeedbackService menuFeedbackService;
    private final MenuReservationService menuReservationService;
    private final ChefAvatarService chefAvatarService;

    @GetMapping("/public/legacy-qr/{qrId}/public-id")
    public ResponseEntity<MenuDtos.PublicIdResponse> resolveLegacyQrPublicId(@PathVariable Long qrId) {
        return ResponseEntity.ok(menuService.resolvePublicIdFromLegacyQrId(qrId));
    }

    @GetMapping("/public/{publicId}")
    public ResponseEntity<MenuDtos.PublicMenuResponse> getPublicMenuByPublicId(@PathVariable String publicId) {
        return ResponseEntity.ok(menuService.getPublicMenuByPublicId(publicId));
    }

    @GetMapping("/public/{publicId}/products")
    public ResponseEntity<MenuDtos.MenuProductPageResponse> listPublicProducts(
            @PathVariable String publicId,
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
                publicId,
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

    @GetMapping("/public/{publicId}/categories")
    public ResponseEntity<TaxonomyDtos.TaxonomyPageResponse> listPublicCategories(
            @PathVariable String publicId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String q
    ) {
        return ResponseEntity.ok(menuService.listPublicCategories(publicId, page, size, q));
    }

    @GetMapping("/public/{publicId}/product-facets")
    public ResponseEntity<MenuDtos.ProductFacetsResponse> listPublicProductFacets(
            @PathVariable String publicId,
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
                publicId,
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

    @GetMapping("/public/{publicId}/products/{productId}/recommendations")
    public ResponseEntity<List<MenuDtos.MenuProductResponse>> listPublicRecommendations(
            @PathVariable String publicId,
            @PathVariable Long productId,
            @RequestParam(defaultValue = "6") int limit
    ) {
        return ResponseEntity.ok(menuService.listPublicRecommendations(publicId, productId, limit));
    }

    @PostMapping("/public/{publicId}/products/{productId}/ratings")
    public ResponseEntity<MenuDtos.ProductRatingResponse> ratePublicProduct(
            @PathVariable String publicId,
            @PathVariable Long productId,
            @Valid @RequestBody MenuDtos.ProductRatingRequest request,
            HttpServletRequest httpRequest
    ) {
        Long menuId = menuService.requirePublicMenuId(publicId);
        return ResponseEntity.status(201).body(
                menuProductRatingService.rateProduct(menuId, productId, request, httpRequest)
        );
    }

    @GetMapping("/public/{publicId}/rating")
    public ResponseEntity<MenuDtos.MenuRatingResponse> getPublicMenuRating(
            @PathVariable String publicId,
            HttpServletRequest httpRequest
    ) {
        Long menuId = menuService.requirePublicMenuId(publicId);
        return ResponseEntity.ok(menuRatingService.getRating(menuId, httpRequest));
    }

    @PostMapping("/public/{publicId}/rating")
    public ResponseEntity<MenuDtos.MenuRatingResponse> ratePublicMenu(
            @PathVariable String publicId,
            @Valid @RequestBody MenuDtos.MenuRatingRequest request,
            HttpServletRequest httpRequest
    ) {
        Long menuId = menuService.requirePublicMenuId(publicId);
        return ResponseEntity.status(201).body(menuRatingService.rateMenu(menuId, request, httpRequest));
    }

    @GetMapping("/{menuId}/feedback")
    @RequiresProductScope(CatalogScopes.QR_MENU_OWNER)
    public ResponseEntity<MenuDtos.FeedbackPageResponse> listFeedback(
            @PathVariable Long menuId,
            @RequestParam(required = false, defaultValue = "all") String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer minScore,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(menuFeedbackService.listFeedback(
                menuId, type, from, to, minScore, page, size
        ));
    }

    @GetMapping("/{menuId}/feedback/summary")
    @RequiresProductScope(CatalogScopes.QR_MENU_OWNER)
    public ResponseEntity<MenuDtos.FeedbackSummaryResponse> feedbackSummary(@PathVariable Long menuId) {
        return ResponseEntity.ok(menuFeedbackService.getSummary(menuId));
    }

    @PostMapping("/public/{publicId}/reservations")
    public ResponseEntity<MenuDtos.ReservationResponse> createPublicReservation(
            @PathVariable String publicId,
            @Valid @RequestBody MenuDtos.ReservationCreateRequest request,
            HttpServletRequest httpRequest
    ) {
        Long menuId = menuService.requirePublicMenuId(publicId);
        return ResponseEntity.status(201).body(
                menuReservationService.createPublic(menuId, request, httpRequest)
        );
    }

    @GetMapping("/{menuId}/reservations")
    @RequiresProductScope(CatalogScopes.QR_MENU_OWNER)
    public ResponseEntity<MenuDtos.ReservationPageResponse> listReservations(
            @PathVariable Long menuId,
            @RequestParam(required = false, defaultValue = "all") String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(menuReservationService.listForOwner(
                menuId, status, from, to, q, page, size
        ));
    }

    @PatchMapping("/{menuId}/reservations/{reservationId}")
    @RequiresProductScope(CatalogScopes.QR_MENU_OWNER)
    public ResponseEntity<MenuDtos.ReservationResponse> updateReservation(
            @PathVariable Long menuId,
            @PathVariable Long reservationId,
            @RequestBody MenuDtos.ReservationUpdateRequest request
    ) {
        return ResponseEntity.ok(menuReservationService.updateForOwner(menuId, reservationId, request));
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
            @Valid @RequestBody MenuDtos.MenuUpdateRequest request
    ) throws Exception {
        return ResponseEntity.ok(menuService.updateMenu(menuId, request));
    }

    @DeleteMapping("/{menuId}")
    @RequiresProductScope(CatalogScopes.QR_MENU_OWNER)
    public ResponseEntity<Void> deleteMenu(@PathVariable Long menuId) {
        menuService.deleteMenu(menuId);
        return ResponseEntity.noContent().build();
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
            @Valid @RequestBody MenuDtos.MenuProductRequest request
    ) {
        return ResponseEntity.status(201).body(menuService.createProduct(menuId, request));
    }

    @PutMapping("/products/{productId}")
    public ResponseEntity<MenuDtos.MenuProductResponse> updateProduct(
            @PathVariable Long productId,
            @Valid @RequestBody MenuDtos.MenuProductRequest request
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
}
