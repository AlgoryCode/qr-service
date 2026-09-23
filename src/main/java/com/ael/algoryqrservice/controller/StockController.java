package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.catalog.CatalogScopes;
import com.ael.algoryqrservice.model.dto.StockDtos;
import com.ael.algoryqrservice.security.RequiresProductScope;
import com.ael.algoryqrservice.service.StockIngredientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/stock")
@RequiredArgsConstructor
public class StockController {

    private final StockIngredientService stockIngredientService;

    @GetMapping("/branches/{branchId}/ingredients")
    @RequiresProductScope(CatalogScopes.QR_MENU_OWNER)
    public ResponseEntity<StockDtos.IngredientPageResponse> list(
            @PathVariable Long branchId,
            @RequestParam(defaultValue = "false") boolean lowOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(stockIngredientService.list(branchId, lowOnly, page, size));
    }

    @PostMapping("/branches/{branchId}/ingredients")
    @RequiresProductScope(CatalogScopes.QR_MENU_OWNER)
    public ResponseEntity<StockDtos.IngredientResponse> create(
            @PathVariable Long branchId,
            @Valid @RequestBody StockDtos.IngredientRequest request
    ) {
        return ResponseEntity.status(201).body(stockIngredientService.create(branchId, request));
    }

    @GetMapping("/ingredients/{ingredientId}")
    @RequiresProductScope(CatalogScopes.QR_MENU_OWNER)
    public ResponseEntity<StockDtos.IngredientResponse> get(@PathVariable Long ingredientId) {
        return ResponseEntity.ok(stockIngredientService.get(ingredientId));
    }

    @PutMapping("/ingredients/{ingredientId}")
    @RequiresProductScope(CatalogScopes.QR_MENU_OWNER)
    public ResponseEntity<StockDtos.IngredientResponse> update(
            @PathVariable Long ingredientId,
            @Valid @RequestBody StockDtos.IngredientUpdateRequest request
    ) {
        return ResponseEntity.ok(stockIngredientService.update(ingredientId, request));
    }

    @DeleteMapping("/ingredients/{ingredientId}")
    @RequiresProductScope(CatalogScopes.QR_MENU_OWNER)
    public ResponseEntity<Void> delete(@PathVariable Long ingredientId) {
        stockIngredientService.delete(ingredientId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/ingredients/{ingredientId}/movements")
    @RequiresProductScope(CatalogScopes.QR_MENU_OWNER)
    public ResponseEntity<StockDtos.MovementPageResponse> listMovements(
            @PathVariable Long ingredientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(stockIngredientService.listMovements(ingredientId, page, size));
    }

    @PostMapping("/ingredients/{ingredientId}/movements")
    @RequiresProductScope(CatalogScopes.QR_MENU_OWNER)
    public ResponseEntity<StockDtos.MovementResponse> recordMovement(
            @PathVariable Long ingredientId,
            @Valid @RequestBody StockDtos.MovementRequest request
    ) {
        return ResponseEntity.status(201).body(stockIngredientService.recordMovement(ingredientId, request));
    }
}
