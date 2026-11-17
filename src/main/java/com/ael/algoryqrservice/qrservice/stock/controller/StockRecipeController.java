package com.ael.algoryqrservice.qrservice.stock.controller;

import com.ael.algoryqrservice.qrservice.stock.dto.StockDtos;
import com.ael.algoryqrservice.qrservice.security.FeatureKey;
import com.ael.algoryqrservice.qrservice.security.RequiresFeature;
import com.ael.algoryqrservice.qrservice.stock.service.StockRecipeService;
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
@RequestMapping("/api/v1/menu/products/{productId}/recipe")
@RequiredArgsConstructor
public class StockRecipeController {

    private final StockRecipeService stockRecipeService;

    @GetMapping
    @RequiresFeature(FeatureKey.QR_MENU)
    public ResponseEntity<StockDtos.RecipeResponse> get(@PathVariable Long productId) {
        return ResponseEntity.ok(stockRecipeService.get(productId));
    }

    @PutMapping
    @RequiresFeature(FeatureKey.QR_MENU)
    public ResponseEntity<StockDtos.RecipeResponse> replace(
            @PathVariable Long productId,
            @Valid @RequestBody StockDtos.RecipeReplaceRequest request
    ) {
        return ResponseEntity.ok(stockRecipeService.replace(productId, request));
    }
}
