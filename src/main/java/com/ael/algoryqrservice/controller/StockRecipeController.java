package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.catalog.CatalogScopes;
import com.ael.algoryqrservice.model.dto.StockDtos;
import com.ael.algoryqrservice.security.RequiresProductScope;
import com.ael.algoryqrservice.service.StockRecipeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/menu/products/{productId}/recipe")
@RequiredArgsConstructor
public class StockRecipeController {

    private final StockRecipeService stockRecipeService;

    @GetMapping
    @RequiresProductScope(CatalogScopes.QR_MENU_OWNER)
    public ResponseEntity<StockDtos.RecipeResponse> get(@PathVariable Long productId) {
        return ResponseEntity.ok(stockRecipeService.get(productId));
    }

    @PutMapping
    @RequiresProductScope(CatalogScopes.QR_MENU_OWNER)
    public ResponseEntity<StockDtos.RecipeResponse> replace(
            @PathVariable Long productId,
            @Valid @RequestBody StockDtos.RecipeReplaceRequest request
    ) {
        return ResponseEntity.ok(stockRecipeService.replace(productId, request));
    }
}
