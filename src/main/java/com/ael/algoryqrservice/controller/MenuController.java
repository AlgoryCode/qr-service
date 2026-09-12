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
import com.ael.algoryqrservice.service.MenuThemeService;
import com.ael.algoryqrservice.model.dto.ThemeDtos;
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
    private final MenuThemeService menuThemeService;

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
            @HttpServletRequest httpRequest
    ) {
        Long menuId = menuService.requirePublicMenuId(publicId);
        return ResponseEntity.ok(menuRatingService.getRating(menuId, httpRequest));
    }
